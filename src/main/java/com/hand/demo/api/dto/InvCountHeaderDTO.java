package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.entity.InvMaterial;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hzero.core.cache.Cacheable;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {

    private List<InvCountLineDTO> invCountLineList;

    private String invCountHeaderErrorMsg;

    private List<IamUserDTO> counterList;

    private List<IamUserDTO> supervisorList;

    private List<MaterialDTO> snapshotMaterialList;

    private List<BatchDTO> snapshotBatchList;

    private int isWMSwarehouse;


}
