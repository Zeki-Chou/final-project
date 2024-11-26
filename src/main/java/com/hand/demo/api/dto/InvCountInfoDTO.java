package com.hand.demo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvCountInfoDTO {
    private List<InvCountHeaderDTO> invalidHeaders;
    private List<InvCountHeaderDTO> validHeaders;
}