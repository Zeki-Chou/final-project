package com.hand.demo.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class InvCountInfoDTO {
    List<InvCountHeaderDTO> invalidHeaderDTOS;
    List<InvCountHeaderDTO> validHeaderDTOS;
    int errSize;
}
