package com.hand.demo.api.controller.v1.DTO;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader {

    private List<InvCountLine> invCountLinesList;

    private String supervisorId;
}
