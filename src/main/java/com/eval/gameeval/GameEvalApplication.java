package com.eval.gameeval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class GameEvalApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameEvalApplication.class, args);
    }

}
