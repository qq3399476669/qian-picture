package com.siqian.qianpicturebackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class RedisStringTest {

    @Resource
    private RedisTemplate redisTemplate;

    @Test
    void stringTest() {
        redisTemplate.opsForValue().set("test1", "hello redis!");
        String test1 = (String) redisTemplate.opsForValue().get("test1");
        System.out.println(test1);
    }

}
