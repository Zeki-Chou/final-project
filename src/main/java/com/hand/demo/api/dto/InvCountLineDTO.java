package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
public class InvCountLineDTO extends InvCountLine {
    @Transient
    private String materialName;
    @Transient
    private String batchCode;
    @Transient
    private List<UserDTO> counterList;

}
