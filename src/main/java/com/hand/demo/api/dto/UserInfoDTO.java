package com.hand.demo.api.dto;

import lombok.Data;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

@Data
public class UserInfoDTO implements Cacheable {
    Long id;

    @CacheValue(key = HZeroCacheKey.USER, primaryKey = "id", searchKey = "realName", structure = CacheValue.DataStructure.MAP_OBJECT)
    String realName;
}
