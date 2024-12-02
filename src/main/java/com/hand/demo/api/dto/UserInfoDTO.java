package com.hand.demo.api.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

@Data
@RequiredArgsConstructor
public class UserInfoDTO implements Cacheable {

    private final Long id;

    @CacheValue(key = HZeroCacheKey.USER, primaryKey = "id", searchKey = "realName", structure = CacheValue.DataStructure.MAP_OBJECT)
    private String realName;
}
