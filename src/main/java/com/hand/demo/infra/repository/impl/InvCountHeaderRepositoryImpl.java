package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.mapper.InvCountHeaderMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvCountHeader)资源库
 *
 * @author Allan
 * @since 2024-11-25 08:19:18
 */
@Component
public class InvCountHeaderRepositoryImpl extends BaseRepositoryImpl<InvCountHeader> implements InvCountHeaderRepository {
    @Resource
    private InvCountHeaderMapper invCountHeaderMapper;

    @Override
    public List<InvCountHeader> selectList(InvCountHeader invCountHeader) {
        return invCountHeaderMapper.selectList(invCountHeader);
    }

    @Override
    public InvCountHeader selectByPrimary(Long countHeaderId) {
        InvCountHeader invCountHeader = new InvCountHeader();
        invCountHeader.setCountHeaderId(countHeaderId);
        List<InvCountHeader> invCountHeaders = invCountHeaderMapper.selectList(invCountHeader);
        if (invCountHeaders.size() == 0) {
            return null;
        }
        return invCountHeaders.get(0);
    }

}

