package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author
 * @since 2024-11-25 08:22:17
 */
@Service
public class InvCountLineServiceImpl implements InvCountLineService {
    @Autowired
    private InvCountLineRepository invCountLineRepository;

    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Override
    public Page<InvCountLineDTO> selectList(PageRequest pageRequest, InvCountLineDTO invCountLine) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLine));
    }

    @Transactional
    @Override
    public void saveData(List<InvCountLineDTO> invCountLines) {
        List<InvCountLine> insertList = invCountLines.stream().filter(line -> line.getCountLineId() == null)
                .collect(Collectors.toList());
        List<InvCountLine> updateList = invCountLines.stream().filter(line -> line.getCountLineId() != null)
                .collect(Collectors.toList());

        insertList.forEach(invCountLine -> {
            invCountLine.setUnitQty(BigDecimal.ZERO);
            invCountLine.setUnitDiffQty(BigDecimal.ZERO);
        });

        invCountLineRepository.batchInsertSelective(insertList);
        updateState(updateList);
//        invCountLineRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    private void updateState(List<InvCountLine> invCountLines) {
        for (InvCountLine invCountLine : invCountLines) {
            String status = invCountHeaderRepository.selectOne(
                    new InvCountHeader().setCountHeaderId(invCountLine.getCountHeaderId()))
                    .getCountStatus();

            List<Long> snapshotIdList = Arrays.stream(invCountLine.getCounterIds().split(","))
                    .map(String::trim)
                    .map(Long::valueOf) // Convert each string to an Integer
                    .collect(Collectors.toList());

            Long currentIdUser = DetailsHelper.getUserDetails().getUserId();

            if (Objects.equals(status, "INCOUNTING")) {
//                if (!Objects.equals(invCountLine.getCreatedBy(), currentIdUser)) {
                    if (!snapshotIdList.contains(currentIdUser)) {
                        throw new CommonException("Error unathorized you are not counters ");
                    }
//                    throw new CommonException("Error unathorized you are not creator");
//                }

                invCountLine.setCounterIds(String.valueOf(DetailsHelper.getUserDetails().getUserId()));
                invCountLineRepository.updateOptional(invCountLine, InvCountLine.FIELD_UNIT_QTY,
                        InvCountLine.FIELD_UNIT_DIFF_QTY, InvCountLine.FIELD_COUNTER_IDS,
                        InvCountLine.FIELD_REMARK);
            }
        }
    }
}

