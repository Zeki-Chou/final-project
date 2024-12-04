package com.hand.demo.domain.repository;

import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.Tenant;

import java.util.List;

/**
 * 租户信息(Tenant)资源库
 *
 * @author
 * @since 2024-12-04 09:44:02
 */
public interface TenantRepository extends BaseRepository<Tenant> {
    /**
     * 查询
     *
     * @param tenant 查询条件
     * @return 返回值
     */
    List<Tenant> selectList(Tenant tenant);

    /**
     * 根据主键查询（可关联表）
     *
     * @param tenantId 主键
     * @return 返回值
     */
    Tenant selectByPrimary(Long tenantId);
}
