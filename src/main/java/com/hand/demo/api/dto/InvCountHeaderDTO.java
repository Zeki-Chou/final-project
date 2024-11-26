package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvMaterial;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader {
    @Transient
    List<UserDTO> counterList;
    @Transient
    List<UserDTO> supervisorList;
    @Transient
    List<InvMaterial> snapshotMaterialList;
    @Transient
    List<InvBatch> snapshotBatchList;
    @Transient
    Boolean isWMSWarehouse;
    @Transient
    List<InvCountLineDTO> invCountLineDTOList;
}
