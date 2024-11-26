package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.infra.mapper.InvCountLineMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author muhammad.azzam@hand-global.com
 * @since 2024-11-25 10:20:03
 */
@Component
public class InvCountLineRepositoryImpl extends BaseRepositoryImpl<InvCountLine> implements InvCountLineRepository {
    @Resource
    private InvCountLineMapper invCountLineMapper;

    @Override
    public List<InvCountLineDTO> selectList(InvCountLineDTO invCountLine) {
        return invCountLineMapper.selectList(invCountLine);
    }

    @Override
    public Long maxLineNumber(){
        return invCountLineMapper.maxLineNumber();
    }

    @Override
    public InvCountLineDTO selectByPrimary(Long countLineId) {
        InvCountLineDTO invCountLine = new InvCountLineDTO();
        invCountLine.setCountLineId(countLineId);
        List<InvCountLineDTO> invCountLines = invCountLineMapper.selectList(invCountLine);
        if (invCountLines.size() == 0) {
            return null;
        }
        return invCountLines.get(0);
    }

}

