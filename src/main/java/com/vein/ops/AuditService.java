package com.vein.ops;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes append-only audit records. Failures to serialise the detail map are
 * logged and swallowed so that auditing never breaks the primary flow.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Long actorId, String action, String target, String ip, Map<String, Object> detail) {
        String json = null;
        if (detail != null && !detail.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(detail);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialise audit detail for action={} target={}", action, target, e);
            }
        }
        repository.save(AuditLog.of(actorId, action, target, ip, json));
    }
}
