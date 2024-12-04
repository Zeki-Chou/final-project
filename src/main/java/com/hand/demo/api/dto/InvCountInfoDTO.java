package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class InvCountInfoDTO {
    private String completeErrMsg;
    private List<InvCountHeaderDTO> errorList = new ArrayList<>();
    private List<InvCountHeaderDTO> successList = new ArrayList<>();
}
