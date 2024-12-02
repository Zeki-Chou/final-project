package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.core.domain.Page;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author muhammad.azzam@hand-global.com
 * @since 2024-11-25 10:20:03
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

    @Override
    public void saveData(List<InvCountLineDTO> invCountLines) {

        int lastLineNumber = Math.toIntExact(invCountLineRepository.maxLineNumber());

        List<InvCountLine> insertList = invCountLines.stream()
                .filter(line -> line.getCountLineId() == null)
                .peek(line -> {
                    line.setLineNumber(lastLineNumber + 1);
                })
                .collect(Collectors.toList());

        List<InvCountLine> updateList = invCountLines.stream()
                .filter(line -> line.getCountLineId() != null)
                .collect(Collectors.toList());

        for (InvCountLine line : updateList) {
            if (line.getCountHeaderId() != null) {

                InvCountHeader countHeader = invCountHeaderRepository.selectByPrimaryKey(line.getCountHeaderId());
                if (countHeader != null) {

                    if (Constants.COUNT_INCOUNTING_STATUS.equals(countHeader.getCountStatus())) {
                        line.setUnitQty(line.getUnitQty());
                        line.setRemark(line.getRemark());
                        line.setUnitDiffQty(line.getUnitDiffQty());
                        if (DetailsHelper.getUserDetails().getUserId().equals(line.getCreatedBy())){
                            line.setCounterIds(countHeader.getCounterIds());
                        }
                    }
                }
            }
        }

        invCountLineRepository.batchInsertSelective(insertList);
        invCountLineRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

}

