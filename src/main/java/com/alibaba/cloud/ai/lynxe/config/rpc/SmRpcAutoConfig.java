package com.alibaba.cloud.ai.lynxe.config.rpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmRpcAutoConfig {
    @Bean
    public LoginUserRequestInterceptor loginUserRequestInterceptor() {

        return new LoginUserRequestInterceptor();
    }

}
