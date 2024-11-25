package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InvCountInfoDTO {
    private List<InvCountHeaderDTO> listErrMsg;
    private List<InvCountHeaderDTO> listSuccessMsg;
    private String errMsg;
    private String successMsg;
}
