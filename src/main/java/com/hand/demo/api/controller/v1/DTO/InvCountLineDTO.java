package com.hand.demo.api.controller.v1.DTO;

import com.hand.demo.domain.entity.InvCountLine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InvCountLineDTO extends InvCountLine implements Cacheable {
    private String batchCode;

    private String materialName;

    @CacheValue(
            key = HZeroCacheKey.USER,
            primaryKey = "createdBy",
            searchKey = "realName",
            structure = CacheValue.DataStructure.MAP_OBJECT
    )
    private String creatorName;
}