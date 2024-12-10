package com.hand.demo.api.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvCountInfoDTO {
    private String errorMsg;
    private String successMsg;

    private List<InvCountHeaderDTO> errorList;
    private List<InvCountHeaderDTO> successList;
}
