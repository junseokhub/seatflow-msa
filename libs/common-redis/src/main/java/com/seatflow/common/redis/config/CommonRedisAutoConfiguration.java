package com.seatflow.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(RedisConfig.class)
public class CommonRedisAutoConfiguration {
}