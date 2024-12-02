package com.hand.demo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IamUserDTO implements Cacheable {
    private Long id;

    @CacheValue(key = HZeroCacheKey.USER, primaryKey = "id", searchKey = "loginName",
            structure = CacheValue.DataStructure.MAP_OBJECT)
    private String loginName;

    @CacheValue(key = HZeroCacheKey.USER, primaryKey = "id", searchKey = "realName",
            structure = CacheValue.DataStructure.MAP_OBJECT)
    private String realName;
}
