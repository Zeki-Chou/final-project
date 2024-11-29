package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.enums.Enums;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.json.JSONObject;
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
    public void saveData(List<InvCountLineDTO> invCountLines) {
        JSONObject iamJSON = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSON.getLong("id");

        List<InvCountLine> insertList = invCountLines.stream().filter(line -> line.getCountLineId() == null).collect(Collectors.toList());
        List<InvCountLine> updateList = invCountLines.stream().filter(line -> line.getCountLineId() != null).collect(Collectors.toList());

        String updateCountHeaderIds = generateStringIds(updateList);
        List<InvCountHeaderDTO> invCountHeaders;
        if (updateCountHeaderIds.isEmpty()) {
            invCountHeaders = new ArrayList<>();
        } else {
            invCountHeaders = invCountHeaderRepository.selectByIds(updateCountHeaderIds);
        }

        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaders
                .stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        updateList.forEach(line -> {
            InvCountHeader header = invCountHeaderMap.get(line.getCountHeaderId());
            //check if header status is in counting
            if (Enums.InvCountHeader.Status.INCOUNTING.name().equals(header.getCountStatus())) {
                // check if current user is counter
                if (!Utils.convertStringIdstoList(header.getCounterIds()).contains(userId)) {
                    line.setUnitQty(null);
                } else {
                    // calculation of difference between the snapshot qty and unit qty
                    BigDecimal unitDiffQty = line.getSnapshotUnitQty().subtract(line.getUnitQty());
                    line.setUnitDiffQty(unitDiffQty);
                }

                if (!userId.equals(header.getCreatedBy())) {
                    line.setCounterIds(null);
                }

            } else {
                line.setUnitQty(null);
                line.setUnitDiffQty(null);
                line.setRemark(null);
            }

        });

        invCountLineRepository.batchInsertSelective(insertList);
        invCountLineRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    private String generateStringIds(List<InvCountLine> invCountLines) {
        Set<String> headerIds = invCountLines
                .stream()
                .map(line -> String.valueOf(line.getCountHeaderId()))
                .collect(Collectors.toSet());

        return String.join(",", headerIds);
    }
}

