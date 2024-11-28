package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.entity.InvMaterial;
import com.hand.demo.domain.entity.InvStock;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InvStockDTO extends InvStock {
    List<InvMaterial> snapshotMaterialList;
    List<InvBatch> snapshotBatchList;
    String dimension;
}
