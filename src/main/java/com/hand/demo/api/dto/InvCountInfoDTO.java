package com.hand.demo.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class InvCountInfoDTO {
    List<InvCountHeaderDTO> errors;
}
