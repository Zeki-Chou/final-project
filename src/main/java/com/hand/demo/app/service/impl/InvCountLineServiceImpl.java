package com.hand.demo.app.service.impl;

import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import groovyjarjarpicocli.CommandLine;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:12:58
 */
@Service
public class InvCountLineServiceImpl implements InvCountLineService {
    @Autowired
    private InvCountLineRepository invCountLineRepository;
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Override
    public Page<InvCountLine> selectList(PageRequest pageRequest, InvCountLine invCountLine) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLine));
    }

    @Override
    public void saveData(List<InvCountLine> invCountLines) {
        //Get Last Line Number
        Integer lastNumber = invCountLineRepository.selectLastNumber();
        int generateLineNumber = lastNumber + 1;

        // Collect and Mapping Header
        Set<Long> requestHeaderIdsSet = invCountLines.stream()
                .map(InvCountLine::getCountHeaderId)
                .collect(Collectors.toSet());

        String countHeaderIdsString = requestHeaderIdsSet.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<InvCountHeader> existingHeaders = invCountHeaderRepository.selectByIds(countHeaderIdsString);

        Map<Long, InvCountHeader> existingHeaderMap = new HashMap<>();
        for (InvCountHeader header : existingHeaders) {
            existingHeaderMap.put(header.getCountHeaderId(), header);
        }

        //Conditioning there request are insert or update
        List<InvCountLine> insertList = new ArrayList<>();
        List<InvCountLine> updateList = new ArrayList<>();

        for (InvCountLine invCountLine : invCountLines) {
            if(invCountLine.getUnitQty() != null && invCountLine.getSnapshotUnitQty() != null){
                invCountLine.setUnitDiffQty(invCountLine.getUnitQty().subtract(invCountLine.getSnapshotUnitQty()).abs());
            }
            if (invCountLine.getCountLineId() == null) {
                invCountLine.setLineNumber(generateLineNumber);
                insertList.add(invCountLine);

                generateLineNumber++;
            } else {
                InvCountHeader header = existingHeaderMap.get(invCountLine.getCountHeaderId());
                if(header.getCountStatus().equals("INCOUNTING")){
                    invCountLine.setCounterIds(header.getCounterIds());
                    updateList.add(invCountLine);
                }
            }
        }

        invCountLineRepository.batchInsertSelective(insertList);
        invCountLineRepository.batchUpdateOptional(updateList,
                InvCountLine.FIELD_UNIT_QTY,
                InvCountLine.FIELD_SNAPSHOT_UNIT_QTY,
                InvCountLine.FIELD_UNIT_DIFF_QTY,
                InvCountLine.FIELD_COUNTER_IDS,
                InvCountLine.FIELD_REMARK
                );
    }
}

