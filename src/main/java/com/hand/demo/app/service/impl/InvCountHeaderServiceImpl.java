package com.hand.demo.app.service.impl;

import com.hand.demo.api.controller.v1.InvCountHeaderController;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.enums.Enums;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * (InvCountHeader)应用服务
 *
 * @author Allan
 * @since 2024-11-25 08:19:18
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {

    private final InvCountHeaderRepository invCountHeaderRepository;
    private final CodeRuleBuilder codeRuleBuilder;
    private final InvCountLineService invCountLineService;
    private final IamRemoteService iamRemoteService;
    private final InvWarehouseRepository invWarehouseRepository;
    private final LovAdapter lovAdapter;

    public InvCountHeaderServiceImpl(
            InvCountHeaderRepository invCountHeaderRepository,
            CodeRuleBuilder codeRuleBuilder,
            InvCountLineService invCountLineService, IamRemoteService iamRemoteService, InvWarehouseRepository invWarehouseRepository, LovAdapter lovAdapter
    ) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.codeRuleBuilder = codeRuleBuilder;
        this.invCountLineService = invCountLineService;
        this.iamRemoteService = iamRemoteService;
        this.invWarehouseRepository = invWarehouseRepository;
        this.lovAdapter = lovAdapter;
    }

    @Override
    public Page<InvCountHeader> selectList(PageRequest pageRequest, InvCountHeader invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        Map<String, List<InvCountLine>> countLineMap = new HashMap<>();

        for (InvCountHeaderDTO header : invCountHeaders) {
            if (header.getCountHeaderId() == null) {
                String countNumber = generateCountNumber();
                header.setCountStatus(Enums.InvCountHeader.Status.DRAFT.name());
                header.setCountNumber(countNumber);
                header.setDelFlag(BaseConstants.Flag.NO);
                countLineMap.put(countNumber, header.getInvCountLineList());
            } else {
                countLineMap.put(header.getCountNumber(), header.getInvCountLineList());
            }
        }

        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        List<InvCountHeader> insertRes = invCountHeaderRepository.batchInsertSelective(insertList);
        List<InvCountHeader> draftUpdateList = filterHeaderListOnStatus(updateList, Enums.InvCountHeader.Status.DRAFT.name());
        List<InvCountHeader> inCountingUpdateList = filterHeaderListOnStatus(updateList, Enums.InvCountHeader.Status.INCOUNTING.name());
        List<InvCountHeader> rejectedUpdateList = filterHeaderListOnStatus(updateList, Enums.InvCountHeader.Status.REJECTED.name());

        List<InvCountHeader> inCountUpdateRes = invCountHeaderRepository.batchUpdateOptional(
                inCountingUpdateList,
                InvCountHeader.FIELD_REMARK,
                InvCountHeader.FIELD_REASON
        );

        List<InvCountHeader> rejectedUpdateRes = invCountHeaderRepository.batchUpdateOptional(
                rejectedUpdateList,
                InvCountHeader.FIELD_REASON
        );

        draftUpdateList.forEach(header -> {
            header.setRemark(null);
            header.setReason(null);
        });

        List<InvCountHeader> draftUpdateRes = invCountHeaderRepository.batchUpdateByPrimaryKeySelective(draftUpdateList);

        List<InvCountHeader> updateRes = Stream.of(draftUpdateRes, inCountUpdateRes, rejectedUpdateRes)
                                                .flatMap(Collection::stream)
                                                .collect(Collectors.toList());

        List<InvCountLine> countLines = new ArrayList<>();

        for (InvCountHeader header : insertRes) {
            List<InvCountLine> invCountLines = countLineMap.get(header.getCountNumber());
            for (InvCountLine line : invCountLines) {
                line.setCountHeaderId(header.getCountHeaderId());
                line.setCounterIds(header.getCounterIds());
            }
            countLines.addAll(invCountLines);
        }

        updateRes.forEach(header -> countLines.addAll(countLineMap.get(header.getCountNumber())));
        invCountLineService.saveData(countLines);
        return invCountHeaders;
    }

    /**
     * generate invoice header number
     * @return invoice header number with format
     */
    private String generateCountNumber() {
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put("customSegment", String.valueOf(BaseConstants.DEFAULT_TENANT_ID));
        return codeRuleBuilder.generateCode(Constants.InvCountHeader.CODE_RULE, variableMap);
    }

    /**
     * filter list of inventory count headers that has specified count status
     * @param invCountHeaders list of inventory count headers
     * @param status status to find
     * @return inventory count header containing status from parameter
     */
    private List<InvCountHeader> filterHeaderListOnStatus(List<InvCountHeader> invCountHeaders, String status) {
        return invCountHeaders
                .stream()
                .filter(header -> status.equals(header.getCountStatus()))
                .collect(Collectors.toList());
    }

    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validHeaderDTO = new ArrayList<>();
        List<InvCountHeaderDTO> invalidHeaderDTO = new ArrayList<>();
        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long currentUser = iamJSONObject.getLong("id");

        invCountHeaderDTOS.forEach(header -> {
            if (!header.getCountStatus().equals(Enums.InvCountHeader.Status.DRAFT.name())) {
                invalidHeaderDTO.add(header);
            }

            if (currentUser.equals(header.getCreatedBy())) {
                invalidHeaderDTO.add(header);
            } else {
                validHeaderDTO.add(header);
            }
        });

        invCountInfoDTO.setValidHeaderDTOS(validHeaderDTO);
        invCountInfoDTO.setInvalidHeaderDTOS(invalidHeaderDTO);
        invCountInfoDTO.setErrSize(invalidHeaderDTO.size());
        return invCountInfoDTO;
    }

    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        InvWarehouse warehouseRecord = new InvWarehouse();
        warehouseRecord.setIsWmsWarehouse(BaseConstants.Flag.YES);

        List<Long> warehouseWMSIds = invWarehouseRepository
                .selectList(warehouseRecord)
                .stream()
                .map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toList());

        List<String> validUpdateStatuses = lovAdapter.queryLovValue(Constants.InvCountHeader.STATUS_LOV_CODE,BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());

        String draftValue = Enums.InvCountHeader.Status.DRAFT.name();
        String inCountingValue = Enums.InvCountHeader.Status.INCOUNTING.name();
        String rejectedValue = Enums.InvCountHeader.Status.REJECTED.name();
        String withdrawnValue = Enums.InvCountHeader.Status.WITHDRAWN.name();

        List<String> validUpdateStatusSupervisorWMS = validUpdateStatuses
                .stream()
                .filter(status ->   status.equals(inCountingValue) ||
                        status.equals(rejectedValue) ||
                        status.equals(withdrawnValue) )
                .collect(Collectors.toList());

        List<InvCountHeaderDTO> invalidHeaderDTOS = new ArrayList<>();
        List<InvCountHeaderDTO> validHeaderDTOS = new ArrayList<>();

        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSONObject.getLong("id");

        // business verifications
        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {

            List<Long> headerCounterIds = Arrays.stream(invCountHeaderDTO.getSupervisorIds().split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            List<Long> supervisorIds = Arrays.stream(invCountHeaderDTO.getCounterIds().split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (invCountHeaderDTO.getCountHeaderId() == null) {
                validHeaderDTOS.add(invCountHeaderDTO);
            } else if (!validUpdateStatuses.contains(invCountHeaderDTO.getCountStatus())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_STATUS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);

            } else if (draftValue.equals(invCountHeaderDTO.getCountStatus()) &&
                    userId.equals(invCountHeaderDTO.getCreatedBy())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_ACCESS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);

            } else if (validUpdateStatusSupervisorWMS.contains(invCountHeaderDTO.getCountStatus())) {

                if (warehouseWMSIds.contains(invCountHeaderDTO.getWarehouseId()) && !supervisorIds.contains(userId)) {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.WAREHOUSE_SUPERVISOR_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }

                if (!headerCounterIds.contains(userId) &&
                        !supervisorIds.contains(userId) &&
                        !invCountHeaderDTO.getCreatedBy().equals(userId))
                {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.ACCESS_UPDATE_STATUS_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }

            } else {
                validHeaderDTOS.add(invCountHeaderDTO);
            }
        }

        invCountInfoDTO.setInvalidHeaderDTOS(invalidHeaderDTOS);
        invCountInfoDTO.setValidHeaderDTOS(validHeaderDTOS);
        invCountInfoDTO.setErrSize(invalidHeaderDTOS.size());
        return invCountInfoDTO;
    }
}

