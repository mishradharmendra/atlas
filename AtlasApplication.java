package com.atlas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Atlas — the JVM domain plane of a decision-grade intelligence platform.
 *
 * <h2>What lives here, and what deliberately does not</h2>
 *
 * <p>This application owns everything that must be <em>transactional, auditable and
 * invariant-protected</em>: who exists, what they may see, what is true and when, what the agent
 * did, what it produced, what it cost, and whether it was right.
 *
 * <p>It owns no inference. No prompt is constructed in this codebase and no model is called from
 * it. Parsing, enrichment, embedding, retrieval and reasoning run in the Python compute plane
 * behind versioned contracts in {@code contracts/}.
 *
 * <p>The split is by <em>determinism</em>, not by feature. Domain invariants need a type system
 * and a transaction manager; machine learning needs a different ecosystem and a much faster
 * release cadence. Keeping them in one process means the slowest-moving part sets the pace for
 * the fastest-moving one, and neither can be tested properly.
 *
 * <h2>Shared modules</h2>
 *
 * <p>{@code shared} is the shared kernel — the ubiquitous language. {@code platform} is
 * cross-cutting infrastructure. Both are declared shared so every module may depend on them
 * without listing them; everything else must be declared explicitly and is enforced by
 * {@code ModularityTest}.
 *
 * <p>{@code com.atlas.contracts} is generated wire format, not a bounded context, and is excluded
 * from module detection in {@code ModularityTest}.
 */
@SpringBootApplication
@Modulithic(sharedModules = {"shared", "platform"})
public class AtlasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtlasApplication.class, args);
    }
}
