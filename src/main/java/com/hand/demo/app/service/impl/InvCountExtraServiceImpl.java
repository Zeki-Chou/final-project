package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountExtraService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountExtra;
import com.hand.demo.domain.repository.InvCountExtraRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * (InvCountExtra)应用服务
 *
 * @author Allan
 * @since 2024-11-28 13:18:22
 */
@Service
@RequiredArgsConstructor
public class InvCountExtraServiceImpl implements InvCountExtraService {

    private final InvCountExtraRepository invCountExtraRepository;

    @Override
    public Page<InvCountExtra> selectList(PageRequest pageRequest, InvCountExtra invCountExtra) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountExtraRepository.selectList(invCountExtra));
    }

    @Override
    public void saveData(List<InvCountExtra> invCountExtras) {
        List<InvCountExtra> insertList = invCountExtras.stream().filter(line -> line.getExtrainfoid() == null).collect(Collectors.toList());
        List<InvCountExtra> updateList = invCountExtras.stream().filter(line -> line.getExtrainfoid() != null).collect(Collectors.toList());
        invCountExtraRepository.batchInsertSelective(insertList);
        invCountExtraRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public InvCountExtra getExtraOrInitialize(List<InvCountExtra> extras, InvCountHeaderDTO countHeader, String programKey) {
        if (extras.isEmpty()) {
            return new InvCountExtra(BaseConstants.Flag.YES, programKey, countHeader.getCountHeaderId(), countHeader.getTenantId());
        } else {
            Map<String, InvCountExtra> extraMap = extras
                    .stream()
                    .collect(Collectors.toMap(InvCountExtra::getProgramkey, Function.identity()));

            return extraMap.get(programKey);
        }
    }
}

