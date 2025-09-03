package com.arch.core_service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@Validated
@ConfigurationProperties(prefix = "spring.redis") // custom prefix (not spring.redis)
public class RedisProperties {

    @NotBlank private String host = "localhost";
    @Min(1) @Max(65_535) private int port = 6379;
    private String username;                 // optional ACL username
    private String password;                 // optional password
    @Min(0) private int database = 0;        // standalone DB index
    @NotNull private Duration timeout = Duration.ofSeconds(2);

    @NestedConfigurationProperty
    @Valid
    private final Cache cache = new Cache();

    @Data
    @NoArgsConstructor
    public static class Cache {
        @NotNull private Duration timeToLive = Duration.ZERO; // 0 = no TTL
        private boolean usePrefix = true;
        private String keyPrefix =
                "${spring.application.name:${random.value}}:${spring.profiles.active:default}:";

        private boolean allowNullValues = false;
        private boolean transactionAware = false;

        // per-cache TTL overrides
        private Map<@NotBlank String, @NotNull Duration> cacheExpirations = new HashMap<>();
    }
}