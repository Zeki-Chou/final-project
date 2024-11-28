package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvCountLine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hzero.core.cache.Cacheable;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    private List<InvCountLine> invCountLineDTOList;
    private String errorMsg;
    private int lineNumber;
    private List<IamUserDTO> counterList;
    private List<IamUserDTO> supervisorList;
    private List<SnapshotMaterialDTO> snapshotMaterialList;
    private List<SnapshotBatchDTO> snapshotBatchList;
    private Integer isWmsWarehouse;
    private String employeeNumber;

}
