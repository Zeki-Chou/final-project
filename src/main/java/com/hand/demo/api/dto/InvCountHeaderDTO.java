package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvCountHeaderDTO extends InvCountHeader {

    private List<InvCountLineDTO> invCountLineList;

    private String invCountHeaderErrorMsg;
}
