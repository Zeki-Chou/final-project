package com.hand.demo.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class InvCountInfoDTO {
    List<String> errorList;
    public InvCountInfoDTO(Integer size){
        setErrorList(Arrays.asList(new String[size]));
    }
}
