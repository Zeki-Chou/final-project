package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.enums.HeaderStatus;
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
    public Page<InvCountLine> selectList(PageRequest pageRequest, InvCountLineDTO invCountLine) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLine));
    }

    @Override
    public void saveData(List<InvCountLineDTO> invCountLines) {
        JSONObject iamJSON = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSON.getLong(Constants.Iam.FIELD_ID);

        List<InvCountLine> insertList = invCountLines.stream().filter(line -> line.getCountLineId() == null).collect(Collectors.toList());
        List<InvCountLine> updateList = invCountLines.stream().filter(line -> line.getCountLineId() != null).collect(Collectors.toList());

        List<Long> headerIdList = updateList.stream().map(InvCountLine::getCountHeaderId).collect(Collectors.toList());
        List<Long> updateLineIdList = updateList.stream().map(InvCountLine::getCountLineId).collect(Collectors.toList());
        String updateCountHeaderIds = Utils.generateStringIds(headerIdList);
        List<InvCountHeader> invCountHeaders = new ArrayList<>();
        List<InvCountLine> countLinesDB = new ArrayList<>();

        if (!updateList.isEmpty()) {
            invCountHeaders.addAll(invCountHeaderRepository.selectByIds(updateCountHeaderIds));
            countLinesDB.addAll(invCountLineRepository.selectByIds(Utils.generateStringIds(updateLineIdList)));
        }

        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaders
                .stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        Map<Long, InvCountLine> countLinesMapDB = countLinesDB
                .stream()
                .collect(Collectors.toMap(InvCountLine::getCountLineId, Function.identity()));

        updateList.forEach(line -> {
            InvCountHeader header = invCountHeaderMap.get(line.getCountHeaderId());
            InvCountLine lineDB = countLinesMapDB.get(line.getCountLineId());
            List<Long> lineCounterIdsDB = Utils.convertStringIdstoList(lineDB.getCounterIds());
            List<Long> lineCounterIds = Utils.convertStringIdstoList(line.getCounterIds());
            boolean validCounterIdsUpdate = lineCounterIds.stream().anyMatch(lineCounterIdsDB::contains);

            // only in counting status can update
            if (HeaderStatus.INCOUNTING.name().equals(header.getCountStatus())) {
                if (!lineCounterIdsDB.contains(userId)) { // check if current user is counter listed in counterIDs
                    line.setUnitQty(null);
                } else { // calculation of difference between the snapshot qty and unit qty
                    BigDecimal unitDiffQty = line.getSnapshotUnitQty().subtract(line.getUnitQty());
                    line.setUnitDiffQty(unitDiffQty);
                }

                if (!validCounterIdsUpdate && userId.equals(header.getCreatedBy())) { // document creator can only modify counter id within the line counter ids
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
}
