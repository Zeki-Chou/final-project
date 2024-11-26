package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author muhammad.azzam@hand-global.com
 * @since 2024-11-22 15:49:56
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {

    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private LovAdapter lovAdapter;

    @Autowired
    private CodeRuleBuilder codeRuleBuilder;

    @Autowired
    private InvCountLineService invCountLineService;

    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveData(List<InvCountHeaderDTO> invCountHeaders) {

        this.manualSaveCheck(invCountHeaders);

        this.manualSave(invCountHeaders);
    }

    private void manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO info = new InvCountInfoDTO();
        String currentUser = DetailsHelper.getUserDetails().getUsername();

        Set<String> modifyStatuses = new HashSet<>(Arrays.asList("DRAFT", "INCOUNTING", "REJECTED", "WITHDRAWN"));
        Set<String> inactiveStatuses = new HashSet<>(Arrays.asList("DRAFT", "REJECTED", "WITHDRAWN"));

        List<InvCountHeaderDTO> invalidHeaders = new ArrayList<>();
        List<InvCountHeaderDTO> validHeaders = new ArrayList<>();

        for (InvCountHeaderDTO dto : invCountHeaders) {
            StringBuilder individualErrorMsgs = new StringBuilder();

            if (dto.getCountHeaderId() == null || dto.getCountHeaderId() == 0) {
                validHeaders.add(dto);
                continue;
            }


            if (!modifyStatuses.contains(dto.getCountStatus())) {
                individualErrorMsgs.append("Only draft, in counting, rejected, and withdrawn status can be modified for CountHeaderId ")
                        .append(dto.getCountHeaderId()).append(".\n");
            }

            if ("DRAFT".equals(dto.getCountStatus()) && !dto.getCreatedBy().toString().equals(currentUser)) {
                individualErrorMsgs.append("Document in draft status can only be modified by the document creator for CountHeaderId ")
                        .append(dto.getCountHeaderId()).append(".\n");
            }

            // Validation for inactive statuses
            if (inactiveStatuses.contains(dto.getCountStatus())) {
                InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimaryKey(dto.getWarehouseId());

                if (invWarehouse.getIsWmsWarehouse().equals(1) && !dto.getSupervisorIds().contains(currentUser)) {
                    individualErrorMsgs.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate for CountHeaderId ")
                            .append(dto.getCountHeaderId()).append(".\n");
                }

                if (dto.getCounterIds().contains(currentUser) || dto.getSupervisorIds().contains(currentUser) || dto.getCreatedBy().toString().equals(currentUser)) {
                    individualErrorMsgs.append("Only the document creator, counter, and supervisor can modify the document for the status of in counting, rejected, withdrawn for CountHeaderId ")
                            .append(dto.getCountHeaderId()).append(".\n");
                }
            }

            if (individualErrorMsgs.length() > 0) {
                dto.setInvCountHeaderErrorMsg(individualErrorMsgs.toString());
                invalidHeaders.add(dto);
            } else {
                validHeaders.add(dto);
            }
        }

        info.setInvalidHeaders(invalidHeaders);

        List<InvCountHeaderDTO> combinedHeaders = new ArrayList<>();
        combinedHeaders.addAll(validHeaders);
        combinedHeaders.addAll(validHeaders);

        info.setValidHeaders(combinedHeaders);
    }

    private void manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        List<String> invalidHeaders = new ArrayList<>();

        //LovValue validation
        List<String> validCountDimension = lovAdapter.queryLovValue(Constants.LovCode.COUNT_DIMENSION, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> validCountType = lovAdapter.queryLovValue(Constants.LovCode.COUNT_TYPE, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> validCountMode = lovAdapter.queryLovValue(Constants.LovCode.COUNT_MODE, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());

        for (int i = 0; i < invCountHeaders.size(); i++) {

            InvCountHeaderDTO dto = invCountHeaders.get(i);

            if (!validCountDimension.contains(dto.getCountDimension())) {
                invalidHeaders.add("Line No" + (i + 1) + " - Invalid Count Dimension: " + dto.getCountDimension());
            }
            if (!validCountType.contains(dto.getCountType())) {
                invalidHeaders.add("Line No" + (i + 1) + " - Invalid Count Type: " + dto.getCountType());
            }
            if (!validCountMode.contains(dto.getCountMode())) {
                invalidHeaders.add("Line No" + (i + 1) + " - Invalid Count Mode: " + dto.getCountMode());
            }
            if (!invalidHeaders.isEmpty()) {
                throw new CommonException("Invalid Count Headers: ", String.join(", ", invalidHeaders));
            }

        }

        // Build code and set default value
        invCountHeaders.forEach(header -> {
            if (header.getCountHeaderId() == null) {
                Map<String, String> variableMap = new HashMap<>();
                variableMap.put("customSegment", ("-" + DetailsHelper.getUserDetails().getTenantId().toString() + "-"));
                String uniqueCode = codeRuleBuilder.generateCode(Constants.CODE_RULE, variableMap);
                header.setCountNumber(uniqueCode);
                header.setCountStatus("DRAFT");
                header.setDelFlag(0);
            }
        });

        //Update data
        List<InvCountHeader> updateList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() != null)
                .collect(Collectors.toList());
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);

        updateList.forEach(header -> {

            if ("DRAFT".equals(header.getCountStatus())) {
                header.setCountDimension(header.getCountDimension());
                header.setCountType(header.getCountType());
                header.setCountMode(header.getCountMode());
                header.setCountTimeStr(header.getCountTimeStr());
                header.setCounterIds(header.getCounterIds());
                header.setSupervisorIds(header.getSupervisorIds());
                header.setSnapshotMaterialIds(header.getSnapshotMaterialIds());
                header.setSnapshotBatchIds(header.getSnapshotBatchIds());
            }

            if ("DRAFT".equals(header.getCountStatus()) || "INCOUNTING".equals(header.getCountStatus())) {
                header.setRemark(header.getRemark());
            }

            if ("INCOUNTING".equals(header.getCountStatus()) || "REJECTED".equals(header.getCountStatus())) {
                header.setReason(header.getReason());
            }
        });

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);

        //Insert data
        List<InvCountHeader> insertList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() == null)
                .collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);

        //Save nested line objects
        invCountHeaders.forEach(header -> {
            header.getInvCountLineList().forEach(line -> line.setCountHeaderId(header.getCountHeaderId()));
            invCountLineService.saveData(header.getInvCountLineList());
        });
    }
}

