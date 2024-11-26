package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.infra.mapper.InvCountLineMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author
 * @since 2024-11-26 08:19:50
 */
@Component
public class InvCountLineRepositoryImpl extends BaseRepositoryImpl<InvCountLineDTO> implements InvCountLineRepository {
    @Resource
    private InvCountLineMapper invCountLineMapper;

    @Override
    public List<InvCountLineDTO> selectList(InvCountLineDTO invCountLineDTO) {
        return invCountLineMapper.selectList(invCountLineDTO);
    }

    @Override
    public InvCountLineDTO selectByPrimary(Long countLineId) {
        InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
        invCountLineDTO.setCountLineId(countLineId);
        List<InvCountLineDTO> invCountLineDTOS = invCountLineMapper.selectList(invCountLineDTO);
        if (invCountLineDTOS.size() == 0) {
            return null;
        }
        return invCountLineDTOS.get(0);
    }

    @Override
    public Integer selectMaxLineNumber(){
        return invCountLineMapper.selectMaxLineNumber();
    }
}

