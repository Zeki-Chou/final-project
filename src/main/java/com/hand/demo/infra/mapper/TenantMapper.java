package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.Tenant;

import java.util.List;

/**
 * 租户信息(Tenant)应用服务
 *
 * @author
 * @since 2024-12-04 09:44:01
 */
public interface TenantMapper extends BaseMapper<Tenant> {
    /**
     * 基础查询
     *
     * @param tenant 查询条件
     * @return 返回值
     */
    List<Tenant> selectList(Tenant tenant);
}

