package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.AllArgsConstructor;
import org.hzero.mybatis.domian.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author
 * @since 2024-11-25 15:22:41
 */
@Service
@AllArgsConstructor
public class InvCountLineServiceImpl implements InvCountLineService {
    private InvCountLineRepository invCountLineRepository;

    private InvCountHeaderRepository invCountHeaderRepository;

    @Override
    public Page<InvCountLineDTO> selectList(PageRequest pageRequest, InvCountLineDTO invCountLine) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLine));
    }

    @Override
    public void saveData(List<InvCountLineDTO> invCountLines) {
        List<InvCountLineDTO> insertList = invCountLines.stream().filter(line -> line.getCountLineId() == null).collect(Collectors.toList());
        List<InvCountLineDTO> updateList = invCountLines.stream().filter(line -> line.getCountLineId() != null).collect(Collectors.toList());

        Long currentLineNumber = invCountLineRepository.getCurrentLineNumber();

        for(InvCountLineDTO countLine : insertList) {
            BigDecimal unitQty = countLine.getUnitQty();
            BigDecimal snapshotUnitQty = countLine.getSnapshotUnitQty();
            if (unitQty != null && snapshotUnitQty != null) {
                countLine.setUnitDiffQty(unitQty.subtract(snapshotUnitQty).abs());
            }
            countLine.setLineNumber(currentLineNumber.intValue()+1);
        }
        invCountLineRepository.batchInsertSelective(new ArrayList<>(insertList));

        String headerIds = updateList.stream()
                .map(InvCountLineDTO::getCountHeaderId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<InvCountHeader> invHeaderList = invCountHeaderRepository.selectByIds(headerIds);

        Map<Long, InvCountHeader> headerById = invHeaderList.stream().collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        for(InvCountLineDTO countLine : updateList) {
            InvCountHeader header = headerById.get(countLine.getCountHeaderId());
            if (header != null) {
                String countStatus = header.getCountStatus();
                if (countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_INCOUNTING)) {
                    invCountLineRepository.updateOptional(countLine, Utils.getNonNullFields(countLine, InvCountLineDTO.FIELD_UNIT_QTY, InvCountLineDTO.FIELD_REMARK));
                    if (isCreator(header.getCreatedBy())) {
                        invCountLineRepository.updateOptional(countLine, Utils.getNonNullFields(InvCountLineDTO.FIELD_COUNTER_IDS));
                    }
                }
            }
        }
//        invCountLineRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    private boolean isCreator(Long creator) {
        return Utils.getCurrentUser().getUserId().equals(creator);
    }
}

