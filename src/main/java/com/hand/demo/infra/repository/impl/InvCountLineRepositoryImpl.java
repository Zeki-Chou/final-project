package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.infra.mapper.InvCountLineMapper;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author Allan
 * @since 2024-11-25 15:46:33
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
    public InvCountLineDTO selectByPrimary(Long countLineId) {
        InvCountLineDTO invCountLine = new InvCountLineDTO();
        invCountLine.setCountLineId(countLineId);
        List<InvCountLineDTO> invCountLines = invCountLineMapper.selectList(invCountLine);
        if (invCountLines.isEmpty()) {
            return null;
        }
        return invCountLines.get(0);
    }

    @Override
    public List<InvCountLineDTO> selectByCountHeaderIds(List<Long> headerIds) {
        return invCountLineMapper.selectByCountHeaderIds(headerIds);
    }

    @Override
    public Integer selectHighestLineNumber() {
        Integer highestLineNumber = invCountLineMapper.selectHighestLineNumber();
        return highestLineNumber == null ? 1 : highestLineNumber;
    }

    @Override
    public List<InvCountLineDTO> selectLineReport(List<Long> headerIds) {
        return invCountLineMapper.selectLineReport(headerIds);
    }

}

