package com.hand.demo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MaterialInfoDTO {
    private final Long id;

    private final String code;
}
