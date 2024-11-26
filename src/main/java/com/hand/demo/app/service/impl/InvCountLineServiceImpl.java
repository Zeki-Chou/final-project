package com.hand.demo.app.service.impl;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.enums.Enums;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author Allan
 * @since 2024-11-25 10:30:32
 */
@Service
public class InvCountLineServiceImpl implements InvCountLineService {

    private final InvCountLineRepository invCountLineRepository;
    private final InvCountHeaderRepository invCountHeaderRepository;
    private final IamRemoteService iamRemoteService;

    public InvCountLineServiceImpl(InvCountLineRepository invCountLineRepository, InvCountHeaderRepository invCountHeaderRepository, IamRemoteService iamRemoteService) {
        this.invCountLineRepository = invCountLineRepository;
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.iamRemoteService = iamRemoteService;
    }

    @Override
    public Page<InvCountLine> selectList(PageRequest pageRequest, InvCountLine invCountLine) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLine));
    }

    @Override
    public void saveData(List<InvCountLine> invCountLines) {
        // select header from count header ids
        String countHeaderIds = generateStringIds(invCountLines);
        List<InvCountHeader> invCountHeaders = invCountHeaderRepository.selectByIds(countHeaderIds);
        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaders
                .stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        List<InvCountLine> insertList = invCountLines.stream().filter(line -> line.getCountLineId() == null).collect(Collectors.toList());

        // only finding inCounting status that needs to be updated
        String inCountingStatus = Enums.InvCountHeader.Status.INCOUNTING.name();
        List<InvCountLine> inCountingUpdateLineList = invCountLines.stream().filter(line -> {
            InvCountHeader header = invCountHeaderMap.get(line.getCountHeaderId());
            return inCountingStatus.equals(header.getCountStatus()) && line.getCountLineId() != null;
        }).collect(Collectors.toList());

        inCountingUpdateLineList.forEach(line -> {
            BigDecimal unitQtyDiff = line.getUnitQty().subtract(line.getUnitQty());
            line.setUnitDiffQty(unitQtyDiff);
        });

        invCountLineRepository.batchInsertSelective(insertList);
        invCountLineRepository.batchUpdateByPrimaryKeySelective(inCountingUpdateLineList);
    }

    private String generateStringIds(List<InvCountLine> invCountLines) {
        Set<String> headerIds = invCountLines
                .stream()
                .filter(line -> line.getCountHeaderId() != null)
                .map(line -> String.valueOf(line.getCountHeaderId()))
                .collect(Collectors.toSet());

        return String.join(",", headerIds);
    }
}

