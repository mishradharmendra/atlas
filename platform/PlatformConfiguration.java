package com.atlas.platform;

import com.atlas.identity.IdentityProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Cross-cutting infrastructure beans. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdentityProperties.class)
public class PlatformConfiguration {

    /**
     * The single source of time for the application.
     *
     * <p>Injected rather than read statically so that "does a snapshot expire after fifteen
     * minutes?" is a test that runs in a millisecond instead of one that sleeps. ArchUnit forbids
     * domain code from reading a clock at all; application services take it as a dependency and
     * pass instants inward.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
