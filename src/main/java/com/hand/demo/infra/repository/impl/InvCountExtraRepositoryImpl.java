package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountExtra;
import com.hand.demo.domain.repository.InvCountExtraRepository;
import com.hand.demo.infra.mapper.InvCountExtraMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvCountExtra)资源库
 *
 * @author
 * @since 2024-11-28 22:33:40
 */
@Component
public class InvCountExtraRepositoryImpl extends BaseRepositoryImpl<InvCountExtra> implements InvCountExtraRepository {
    @Resource
    private InvCountExtraMapper invCountExtraMapper;

    @Override
    public List<InvCountExtra> selectList(InvCountExtra invCountExtra) {
        return invCountExtraMapper.selectList(invCountExtra);
    }

    @Override
    public InvCountExtra selectByPrimary(Long extrainfoid) {
        InvCountExtra invCountExtra = new InvCountExtra();
        invCountExtra.setExtrainfoid(extrainfoid);
        List<InvCountExtra> invCountExtras = invCountExtraMapper.selectList(invCountExtra);
        if (invCountExtras.size() == 0) {
            return null;
        }
        return invCountExtras.get(0);
    }

}

