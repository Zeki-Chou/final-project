package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Accessors(chain = true)
public class InvCountInfoDTO {
    private Set<InvCountHeaderDTO> successList = new HashSet<>();
    private Set<InvCountHeaderDTO> errorList = new HashSet<>();
}
