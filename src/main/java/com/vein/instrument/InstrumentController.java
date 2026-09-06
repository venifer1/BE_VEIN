package com.vein.instrument;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/instruments")
@Tag(name = "Instruments", description = "Instrument catalog & search")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    @Operation(summary = "Search instruments",
            description = "Unified 4-market search by symbol/name or Korean 초성 (e.g. 'ㅅㅈ'). "
                    + "?market=CRYPTO|US|KOSPI|KOSDAQ filters; omit for all markets. Max 30.")
    public ApiResponse<List<InstrumentDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String status) {
        return ApiResponse.of(instrumentService.search(q, market, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Instrument detail")
    public ApiResponse<InstrumentDto> detail(@PathVariable Long id) {
        return ApiResponse.of(InstrumentDto.from(instrumentService.getById(id)));
    }
}
