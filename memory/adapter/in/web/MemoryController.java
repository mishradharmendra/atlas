package com.atlas.memory.adapter.in.web;

import com.atlas.memory.api.MemoryApi;
import com.atlas.memory.api.MemoryView;
import com.atlas.memory.api.NewMemory;
import com.atlas.platform.tenant.RequestTenant;
import com.atlas.platform.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * What an agent remembers, scoped to the credential that is asking.
 *
 * <p>Tenant comes from the verified snapshot and never from the body. Memory is the one store
 * where a tenant mix-up is silent: an entry recalled from the wrong tenant arrives as a sentence
 * in a prompt, not as a row with an owner, and nothing downstream can tell it apart.
 */
@RestController
@RequestMapping("/api/v1/memory")
class MemoryController {

    private final MemoryApi memory;

    MemoryController(MemoryApi memory) {
        this.memory = memory;
    }

    @PostMapping(value = "/entries", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> remember(@RequestBody RememberRequest body) {
        RequestTenant caller = TenantContext.require();
        String id = memory.remember(new NewMemory(
                caller.tenantId(),
                body.kind(),
                body.text(),
                body.writtenByRun(),
                body.sourceDocIds() == null ? Set.of() : body.sourceDocIds(),
                body.entitlementTags() == null ? Set.of() : body.entitlementTags(),
                body.expiresAt() == null ? null : Instant.parse(body.expiresAt())));
        return Map.of("entryId", id);
    }

    /**
     * Recall under the caller's own denied-label-free tags.
     *
     * <p>The tags travel in the query rather than being inferred, so a caller asking for more
     * than it holds is a request the platform can refuse rather than a default it cannot see.
     */
    @GetMapping(value = "/entries", produces = MediaType.APPLICATION_JSON_VALUE)
    List<MemoryView> recall(
            @RequestParam(defaultValue = "") String tags,
            @RequestParam(required = false) String at,
            @RequestParam(defaultValue = "20") int limit) {

        RequestTenant caller = TenantContext.require();
        Set<String> permitted = tags.isBlank()
                ? Set.of()
                : Set.of(tags.split("\\s*,\\s*"));
        return memory.recall(
                caller.tenantId(), permitted, at == null ? null : Instant.parse(at), limit);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> refused(RuntimeException e) {
        return Map.of("message", e.getMessage());
    }

    record RememberRequest(
            String kind,
            String text,
            String writtenByRun,
            Set<String> sourceDocIds,
            Set<String> entitlementTags,
            String expiresAt) {}
}
