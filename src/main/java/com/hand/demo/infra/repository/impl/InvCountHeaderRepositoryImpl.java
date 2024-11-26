package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
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
 * @author
 * @since 2024-11-25 09:59:38
 */
@Component
public class InvCountHeaderRepositoryImpl extends BaseRepositoryImpl<InvCountHeaderDTO> implements InvCountHeaderRepository {
    @Resource
    private InvCountHeaderMapper invCountHeaderMapper;

    @Override
    public List<InvCountHeaderDTO> selectList(InvCountHeaderDTO invCountHeaderDTO) {
        return invCountHeaderMapper.selectList(invCountHeaderDTO);
    }

    @Override
    public InvCountHeaderDTO selectByPrimary(Long countHeaderId) {
        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        invCountHeaderDTO.setCountHeaderId(countHeaderId);
        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountHeaderMapper.selectList(invCountHeaderDTO);
        if (invCountHeaderDTOS.size() == 0) {
            return null;
        }
        return invCountHeaderDTOS.get(0);
    }

}

