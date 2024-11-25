package com.hand.demo.api.controller.v1.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class InvCountException extends RuntimeException{
    private final InvCountInfoDTO invCountInfoDTO;
}
