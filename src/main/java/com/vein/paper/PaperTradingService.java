package com.vein.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.candle.CandleRepository;
import com.vein.paper.PaperDto.AccountResponse;
import com.vein.paper.PaperDto.CreateAccountRequest;
import com.vein.paper.PaperDto.CreateOrderRequest;
import com.vein.paper.PaperDto.FillResponse;
import com.vein.paper.PaperDto.OrderResponse;
import com.vein.paper.PaperDto.PerformanceResponse;
import com.vein.paper.PaperDto.PortfolioResponse;
import com.vein.paper.PaperDto.PositionResponse;
import com.vein.signal.PatternSignalRepository;

@Service
@Transactional(readOnly = true)
public class PaperTradingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DEFAULT_CURRENCY = "KRW";
    private static final String DEFAULT_TIMEFRAME = "1d";
    private static final String FEE_MODEL_VERSION = "paper-flat-0.05pct-v1";
    private static final BigDecimal FEE_RATE = new BigDecimal("0.0005");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PaperAccountRepository accountRepository;
    private final PaperOrderRepository orderRepository;
    private final PaperFillRepository fillRepository;
    private final PaperPositionRepository positionRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final InstrumentRepository instrumentRepository;
    private final CandleRepository candleRepository;
    private final PatternSignalRepository signalRepository;

    public PaperTradingService(PaperAccountRepository accountRepository,
                               PaperOrderRepository orderRepository,
                               PaperFillRepository fillRepository,
                               PaperPositionRepository positionRepository,
                               LedgerEntryRepository ledgerRepository,
                               InstrumentRepository instrumentRepository,
                               CandleRepository candleRepository,
                               PatternSignalRepository signalRepository) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.positionRepository = positionRepository;
        this.ledgerRepository = ledgerRepository;
        this.instrumentRepository = instrumentRepository;
        this.candleRepository = candleRepository;
        this.signalRepository = signalRepository;
    }

    @Transactional
    public AccountResponse createAccount(Long userId, CreateAccountRequest request) {
        List<PaperAccount> active = accountRepository.findByUserIdAndStatus(userId, ACTIVE);
        int nextRun = active.stream().mapToInt(PaperAccount::getSimulationRun).max().orElse(0) + 1;
        active.forEach(PaperAccount::archive);

        BigDecimal initialBalance = positive(request.initialBalance(), "initial_balance");
        String baseCurrency = request.baseCurrency() == null || request.baseCurrency().isBlank()
                ? DEFAULT_CURRENCY
                : request.baseCurrency().trim().toUpperCase();
        PaperAccount account = accountRepository.save(
                PaperAccount.create(userId, baseCurrency, initialBalance, nextRun));
        ledgerRepository.save(LedgerEntry.of(account.getId(), "DEPOSIT", initialBalance,
                baseCurrency, "ACCOUNT", account.getId(), "paper-account-" + account.getId()));
        return toAccount(account);
    }

    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        PaperAccount account = activeAccount(userId);
        Instrument instrument = instrumentRepository.findById(request.instrumentId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Instrument not found"));
        if (request.signalId() != null && !signalRepository.existsById(request.signalId())) {
            throw new ApiException(ErrorCode.SIGNAL_NOT_FOUND);
        }

        String side = normalizeSide(request.side());
        String type = normalizeType(request.type());
        String investmentType = normalizeInvestmentType(request.investmentType());
        String positionSide = normalizePositionSide(request.positionSide(), side, investmentType);
        BigDecimal leverage = leverage(request.leverage(), investmentType);
        boolean reduceOnly = Boolean.TRUE.equals(request.reduceOnly());
        BigDecimal quantity = positive(request.quantity(), "quantity");
        BigDecimal fillPrice = resolveFillPrice(request, request.instrumentId());
        BigDecimal notional = fillPrice.multiply(quantity);
        BigDecimal fee = notional.multiply(FEE_RATE).setScale(8, RoundingMode.HALF_UP);

        PaperOrder order = orderRepository.save(PaperOrder.create(account.getId(),
                request.instrumentId(), request.signalId(), investmentType, positionSide, side, type,
                fillPrice, quantity, leverage, reduceOnly, FEE_MODEL_VERSION));
        PaperFill fill = fillRepository.save(PaperFill.of(order.getId(), fillPrice, quantity,
                fee, request.price() == null || request.price().isBlank() ? "LATEST_CANDLE" : "REQUEST_PRICE"));

        if ("FUTURES".equals(investmentType)) {
            applyFutures(account, instrument, positionSide, fillPrice, quantity, notional, fee,
                    leverage, reduceOnly, order.getId());
        } else if ("BUY".equals(side)) {
            applyBuy(account, instrument, fillPrice, quantity, notional, fee, order.getId());
        } else {
            applySell(account, instrument, fillPrice, quantity, notional, fee, order.getId());
        }
        order.fill(fillPrice);
        return toOrder(order, fill, instrument);
    }

    public PortfolioResponse portfolio(Long userId) {
        PaperAccount account = activeAccount(userId);
        List<PaperPosition> positions = positionRepository.findByAccountId(account.getId());
        List<PositionResponse> positionDtos = positions.stream()
                .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(this::toPosition)
                .toList();

        BigDecimal unrealizedPnl = positionDtos.stream()
                .map(p -> new BigDecimal(p.unrealizedPnl()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal realizedPnl = positions.stream()
                .map(PaperPosition::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marketValue = positionDtos.stream()
                .map(p -> new BigDecimal(p.marketValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal equity = account.getCashBalance().add(marketValue);

        List<PaperOrder> orders = orderRepository.findTop20ByAccountIdOrderByCreatedAtDesc(account.getId());
        // 종목명을 배치 로드해 주문 내역에 심볼/이름을 채운다(내역이 "#id"로 보이던 것 개선, R91). N+1 회피.
        Map<Long, Instrument> orderInstruments = instrumentRepository.findAllById(
                        orders.stream().map(PaperOrder::getInstrumentId).distinct().toList())
                .stream().collect(Collectors.toMap(Instrument::getId, Function.identity()));
        List<OrderResponse> recentOrders = orders.stream()
                .map(order -> toOrder(order, null, orderInstruments.get(order.getInstrumentId())))
                .toList();

        return new PortfolioResponse(toAccount(account), money(equity), money(unrealizedPnl),
                money(realizedPnl), positionDtos, recentOrders);
    }

    public PerformanceResponse performance(Long userId) {
        PortfolioResponse portfolio = portfolio(userId);
        BigDecimal equity = new BigDecimal(portfolio.equity());
        BigDecimal initial = new BigDecimal(portfolio.account().initialBalance());
        BigDecimal totalReturnPct = initial.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : equity.subtract(initial).divide(initial, 8, RoundingMode.HALF_UP).multiply(HUNDRED);
        return new PerformanceResponse(portfolio.account().id(), pct(totalReturnPct), portfolio.equity(),
                portfolio.realizedPnl(), portfolio.unrealizedPnl(), portfolio.positions().size());
    }

    private void applyBuy(PaperAccount account, Instrument instrument, BigDecimal fillPrice,
                          BigDecimal quantity, BigDecimal notional, BigDecimal fee, Long orderId) {
        BigDecimal totalCost = notional.add(fee);
        if (account.getCashBalance().compareTo(totalCost) < 0) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Insufficient paper cash");
        }
        account.subtractCash(totalCost);
        PaperPosition position = positionRepository
                .findByAccountIdAndInstrumentId(account.getId(), instrument.getId())
                .orElseGet(() -> positionRepository.save(
                        PaperPosition.create(account.getId(), instrument.getId(), "SPOT", null,
                                BigDecimal.ZERO, fillPrice, BigDecimal.ZERO, BigDecimal.ONE)));
        if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            position.buy(fillPrice, quantity);
        } else {
            position.buy(fillPrice, quantity);
        }
        ledgerRepository.save(LedgerEntry.of(account.getId(), "FILL", totalCost.negate(),
                account.getBaseCurrency(), "ORDER", orderId, "paper-order-" + orderId + "-buy"));
    }

    private void applySell(PaperAccount account, Instrument instrument, BigDecimal fillPrice,
                           BigDecimal quantity, BigDecimal notional, BigDecimal fee, Long orderId) {
        PaperPosition position = positionRepository
                .findByAccountIdAndInstrumentId(account.getId(), instrument.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_QUERY, "No open paper position"));
        if (position.getQuantity().compareTo(quantity) < 0) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Sell quantity exceeds open paper position");
        }
        position.sell(fillPrice, quantity, fee);
        account.addCash(notional.subtract(fee));
        ledgerRepository.save(LedgerEntry.of(account.getId(), "FILL", notional.subtract(fee),
                account.getBaseCurrency(), "ORDER", orderId, "paper-order-" + orderId + "-sell"));
    }

    private void applyFutures(PaperAccount account, Instrument instrument, String positionSide,
                              BigDecimal fillPrice, BigDecimal quantity, BigDecimal notional,
                              BigDecimal fee, BigDecimal leverage, boolean reduceOnly, Long orderId) {
        PaperPosition position = positionRepository
                .findByAccountIdAndInstrumentIdAndInvestmentTypeAndPositionSide(
                        account.getId(), instrument.getId(), "FUTURES", positionSide)
                .orElse(null);

        if (reduceOnly) {
            if (position == null || position.getQuantity().compareTo(quantity) < 0) {
                throw new ApiException(ErrorCode.INVALID_QUERY, "Close quantity exceeds futures position");
            }
            BigDecimal cashIn = position.closeFutures(fillPrice, quantity, fee);
            account.addCash(cashIn);
            ledgerRepository.save(LedgerEntry.of(account.getId(), "FUTURES_CLOSE", cashIn,
                    account.getBaseCurrency(), "ORDER", orderId, "paper-order-" + orderId + "-futures-close"));
            return;
        }

        BigDecimal margin = notional.divide(leverage, 8, RoundingMode.HALF_UP);
        BigDecimal totalCost = margin.add(fee);
        if (account.getCashBalance().compareTo(totalCost) < 0) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Insufficient paper margin");
        }
        account.subtractCash(totalCost);
        if (position == null) {
            positionRepository.save(PaperPosition.create(account.getId(), instrument.getId(),
                    "FUTURES", positionSide, quantity, fillPrice, margin, leverage));
        } else {
            position.increase(fillPrice, quantity, margin, leverage);
        }
        ledgerRepository.save(LedgerEntry.of(account.getId(), "FUTURES_OPEN", totalCost.negate(),
                account.getBaseCurrency(), "ORDER", orderId, "paper-order-" + orderId + "-futures-open"));
    }

    private PaperAccount activeAccount(Long userId) {
        return accountRepository.findTopByUserIdAndStatusOrderBySimulationRunDesc(userId, ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Paper account not found"));
    }

    private BigDecimal resolveFillPrice(CreateOrderRequest request, Long instrumentId) {
        if (request.price() != null && !request.price().isBlank()) {
            return positive(request.price(), "price");
        }
        String timeframe = request.timeframe() == null || request.timeframe().isBlank()
                ? DEFAULT_TIMEFRAME
                : request.timeframe();
        return candleRepository.findTopByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeDesc(instrumentId, timeframe)
                .or(() -> candleRepository.findTopByIdInstrumentIdOrderByIdOpenTimeDesc(instrumentId))
                .map(c -> c.getClose())
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_PARTIAL, "No candle price for paper fill"));
    }

    private PositionResponse toPosition(PaperPosition position) {
        Instrument instrument = instrumentRepository.findById(position.getInstrumentId()).orElse(null);
        BigDecimal markPrice = candleRepository.findTopByIdInstrumentIdOrderByIdOpenTimeDesc(position.getInstrumentId())
                .map(c -> c.getClose())
                .orElse(position.getAvgPrice());
        BigDecimal direction = "SHORT".equals(position.getPositionSide()) ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
        BigDecimal marketValue = markPrice.multiply(position.getQuantity());
        BigDecimal unrealizedPnl = markPrice.subtract(position.getAvgPrice()).multiply(position.getQuantity()).multiply(direction);
        return new PositionResponse(position.getInstrumentId(),
                instrument == null ? null : instrument.getSymbol(),
                instrument == null ? null : instrument.getName(),
                qty(position.getQuantity()), position.getInvestmentType(), position.getPositionSide(),
                money(position.getAvgPrice()), money(markPrice), money(marketValue),
                money(position.getMargin()), qty(position.getLeverage()),
                money(unrealizedPnl), money(position.getRealizedPnl()));
    }

    private AccountResponse toAccount(PaperAccount account) {
        return new AccountResponse("paper_" + account.getId(), account.getBaseCurrency(),
                money(account.getInitialBalance()), money(account.getCashBalance()),
                account.getStatus(), account.getSimulationRun());
    }

    private OrderResponse toOrder(PaperOrder order, PaperFill fill, Instrument instrument) {
        return new OrderResponse("pord_" + order.getId(), "paper_" + order.getAccountId(),
                order.getInstrumentId(),
                instrument == null ? null : instrument.getSymbol(),
                instrument == null ? null : instrument.getName(),
                order.getSignalId(), order.getInvestmentType(),
                order.getPositionSide(), order.getSide(), order.getType(),
                order.getPrice() == null ? null : money(order.getPrice()), qty(order.getQuantity()),
                qty(order.getLeverage()), order.isReduceOnly(),
                order.getStatus(), fill == null ? null : toFill(fill));
    }

    private FillResponse toFill(PaperFill fill) {
        return new FillResponse("pfill_" + fill.getId(), money(fill.getPrice()), qty(fill.getQuantity()),
                money(fill.getFee()), money(fill.getSlippage()), fill.getLiquiditySource(),
                TimeUtil.toIso(fill.getFilledAt()));
    }

    private static String normalizeSide(String side) {
        if (side == null || side.isBlank()) {
            return "BUY";
        }
        String normalized = side.trim().toUpperCase();
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Unsupported paper order side");
        }
        return normalized;
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "MARKET";
        }
        String normalized = type.trim().toUpperCase();
        if (!"MARKET".equals(normalized) && !"LIMIT".equals(normalized)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Unsupported paper order type");
        }
        return normalized;
    }

    private static String normalizeInvestmentType(String investmentType) {
        if (investmentType == null || investmentType.isBlank()) {
            return "SPOT";
        }
        String normalized = investmentType.trim().toUpperCase();
        if (!"SPOT".equals(normalized) && !"FUTURES".equals(normalized)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Unsupported paper investment type");
        }
        return normalized;
    }

    private static String normalizePositionSide(String positionSide, String side, String investmentType) {
        if (!"FUTURES".equals(investmentType)) {
            return null;
        }
        if (positionSide == null || positionSide.isBlank()) {
            return "SELL".equals(side) ? "SHORT" : "LONG";
        }
        String normalized = positionSide.trim().toUpperCase();
        if (!"LONG".equals(normalized) && !"SHORT".equals(normalized)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Unsupported futures position side");
        }
        return normalized;
    }

    private static BigDecimal leverage(String raw, String investmentType) {
        if (!"FUTURES".equals(investmentType)) {
            return BigDecimal.ONE;
        }
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ONE;
        }
        BigDecimal value = positive(raw, "leverage");
        if (value.compareTo(new BigDecimal("50")) > 0) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Paper futures leverage must be 50x or lower");
        }
        return value;
    }

    private static BigDecimal positive(String raw, String field) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Invalid " + field);
        }
    }

    static String money(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String qty(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String pct(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
