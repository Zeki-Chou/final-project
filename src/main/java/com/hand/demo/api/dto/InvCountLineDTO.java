package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InvCountLineDTO extends InvCountLine {
    private String errMsg;
    private String materialName;
    private String materialCode;
    private String batchCode;
    private List<IamDTO> counter;
}
