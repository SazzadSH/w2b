package com.w2b.walletservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.w2b.walletservice.repository.jpa")
@EntityScan("com.w2b.walletservice.domain")
public class JPAConfig {
}
