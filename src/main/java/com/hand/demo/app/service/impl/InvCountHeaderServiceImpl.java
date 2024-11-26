package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.app.service.InvWarehouseService;
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

        InvCountInfoDTO saveValidation = this.manualSaveCheck(invCountHeaders);

        if (saveValidation.getErrorMsg() != null && !saveValidation.getErrorMsg().isEmpty()) {
            throw new CommonException(saveValidation.getErrorMsg());
        }

        this.manualSave(invCountHeaders);
    }

    private InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO info = new InvCountInfoDTO();
        String currentUser = DetailsHelper.getUserDetails().getUsername();
        StringBuilder errorMessages = new StringBuilder();

        Set<String> modifyStatuses = new HashSet<>(Arrays.asList("DRAFT", "INCOUNTING", "REJECTED", "WITHDRAWN"));
        Set<String> inactiveStatuses = new HashSet<>(Arrays.asList("DRAFT", "REJECTED", "WITHDRAWN"));

        List<InvCountHeaderDTO> headerErrorMsgs = new ArrayList<>();

        for (InvCountHeaderDTO dto : invCountHeaders) {
            if (dto.getCountHeaderId() == null) {
                continue;
            }

            boolean hasError = false;
            StringBuilder individualErrorMsgs = new StringBuilder();

            if (!modifyStatuses.contains(dto.getCountStatus())) {
                individualErrorMsgs.append("Only draft, in counting, rejected, and withdrawn status can be modified for CountHeaderId ")
                        .append(dto.getCountHeaderId()).append(".\n");
                hasError = true;
            }

            if ("DRAFT".equals(dto.getCountStatus()) && !dto.getCreatedBy().toString().equals(currentUser)) {
                individualErrorMsgs.append("Document in draft status can only be modified by the document creator for CountHeaderId ")
                        .append(dto.getCountHeaderId()).append(".\n");
                hasError = true;
            }

            if (inactiveStatuses.contains(dto.getCountStatus())) {
                InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimaryKey(dto.getWarehouseId());

                if (invWarehouse.getIsWmsWarehouse().equals(1) && !dto.getSupervisorIds().contains(currentUser)) {
                    individualErrorMsgs.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate for CountHeaderId ")
                            .append(dto.getCountHeaderId()).append(".\n");
                    hasError = true;
                }

                if (dto.getCounterIds().contains(currentUser) || dto.getSupervisorIds().contains(currentUser) || dto.getCreatedBy().toString().equals(currentUser)) {
                    individualErrorMsgs.append("Only the document creator, counter, and supervisor can modify the document for the status of in counting, rejected, withdrawn for CountHeaderId ")
                            .append(dto.getCountHeaderId()).append(".\n");
                    hasError = true;
                }
            }

            if (hasError) {
                dto.setInvCountHeaderErrorMsg(individualErrorMsgs.toString());
                headerErrorMsgs.add(dto);
                errorMessages.append(individualErrorMsgs);
            }
        }

        info.setInvalidHeaders(headerErrorMsgs);
        info.setErrorMsg(errorMessages.toString());

        return info;
    }


    private void manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        List<String> invalidHeaders = new ArrayList<>();

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

        invCountHeaders.forEach(header -> {
            if(header.getCountHeaderId() == null){
                Map<String, String> variableMap = new HashMap<>();
                variableMap.put("customSegment", ("-" + DetailsHelper.getUserDetails().getTenantId().toString() + "-"));
                String uniqueCode = codeRuleBuilder.generateCode(Constants.CODE_RULE, variableMap);
                header.setCountNumber(uniqueCode);
                header.setCountStatus("DRAFT");
            }
        });

        List<InvCountHeader> updateList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() != null)
                .collect(Collectors.toList());
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);

        List<InvCountHeader> insertList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() == null)
                .collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);

        invCountHeaders.forEach(header -> {
            header.getInvCountLineList().forEach(line -> line.setCountHeaderId(header.getCountHeaderId()));
            invCountLineService.saveData(header.getInvCountLineList());
        });
    }


}

