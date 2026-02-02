package com.eval.gameeval.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TokenUtil {
    public String generateToken(){
        return UUID.randomUUID().toString().replace("-","");
    }
}
