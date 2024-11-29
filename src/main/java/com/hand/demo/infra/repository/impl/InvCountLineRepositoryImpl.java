package com.hand.demo.infra.repository.impl;

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
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:12:58
 */
@Component
public class InvCountLineRepositoryImpl extends BaseRepositoryImpl<InvCountLine> implements InvCountLineRepository {
    @Resource
    private InvCountLineMapper invCountLineMapper;

    @Override
    public List<InvCountLine> selectList(InvCountLine invCountLine) {
        return invCountLineMapper.selectList(invCountLine);
    }

    @Override
    public InvCountLine selectByPrimary(Long countLineId) {
        InvCountLine invCountLine = new InvCountLine();
        invCountLine.setCountLineId(countLineId);
        List<InvCountLine> invCountLines = invCountLineMapper.selectList(invCountLine);
        if (invCountLines.size() == 0) {
            return null;
        }
        return invCountLines.get(0);
    }

    @Override
    public Integer selectLastNumber(){
        return invCountLineMapper.selectLastNumber();
    }

}

