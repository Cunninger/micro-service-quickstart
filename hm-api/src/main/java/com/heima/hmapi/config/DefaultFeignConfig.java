package com.heima.hmapi.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;



/**
 * 功能：
 * 日期：2024/9/5 下午8:19
 */
public class DefaultFeignConfig {
    @Bean
    public Logger.Level feignLoggerLevel(){
        return Logger.Level.FULL;
    }
}