package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.Tenant;
import com.hand.demo.domain.repository.TenantRepository;
import com.hand.demo.infra.mapper.TenantMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * 租户信息(Tenant)资源库
 *
 * @author
 * @since 2024-12-04 09:44:02
 */
@Component
public class TenantRepositoryImpl extends BaseRepositoryImpl<Tenant> implements TenantRepository {
    @Resource
    private TenantMapper tenantMapper;

    @Override
    public List<Tenant> selectList(Tenant tenant) {
        return tenantMapper.selectList(tenant);
    }

    @Override
    public Tenant selectByPrimary(Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        List<Tenant> tenants = tenantMapper.selectList(tenant);
        if (tenants.size() == 0) {
            return null;
        }
        return tenants.get(0);
    }

}

