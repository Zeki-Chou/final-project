package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class InvCountInfoDTO {
    private List<InvCountHeaderDTO> successList = new ArrayList<>();
    private List<InvCountHeaderDTO> errorList = new ArrayList<>();
}
