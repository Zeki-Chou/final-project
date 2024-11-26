package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
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
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    private final IamRemoteService iamRemoteService;
    private final InvWarehouseRepository invWarehouseRepository;
    private final LovAdapter lovAdapter;
    private final InvMaterialRepository invMaterialRepository;
    private final InvBatchRepository invBatchRepository;

    public InvCountHeaderServiceImpl(
            InvCountHeaderRepository invCountHeaderRepository,
            CodeRuleBuilder codeRuleBuilder,
            IamRemoteService iamRemoteService,
            InvWarehouseRepository invWarehouseRepository,
            LovAdapter lovAdapter,
            InvMaterialRepository invMaterialRepository,
            InvBatchRepository invBatchRepository
    ) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.codeRuleBuilder = codeRuleBuilder;
        this.iamRemoteService = iamRemoteService;
        this.invWarehouseRepository = invWarehouseRepository;
        this.lovAdapter = lovAdapter;
        this.invMaterialRepository = invMaterialRepository;
        this.invBatchRepository = invBatchRepository;
    }

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        insertList.forEach(header -> {
            header.setCountStatus(Enums.InvCountHeader.Status.DRAFT.name());
            header.setCountNumber(generateCountNumber());
            header.setDelFlag(BaseConstants.Flag.NO);
        });

        invCountHeaderRepository.batchInsertSelective(insertList);
        List<InvCountHeader> draftUpdateList = filterHeaderListByStatus(updateList, Enums.InvCountHeader.Status.DRAFT.name());
        List<InvCountHeader> inCountingUpdateList = filterHeaderListByStatus(updateList, Enums.InvCountHeader.Status.INCOUNTING.name());
        List<InvCountHeader> rejectedUpdateList = filterHeaderListByStatus(updateList, Enums.InvCountHeader.Status.REJECTED.name());

        invCountHeaderRepository.batchUpdateOptional(
                inCountingUpdateList,
                InvCountHeader.FIELD_REMARK,
                InvCountHeader.FIELD_REASON
        );

        invCountHeaderRepository.batchUpdateOptional(
                rejectedUpdateList,
                InvCountHeader.FIELD_REASON
        );

        draftUpdateList.forEach(header -> {
            header.setRemark(null);
            header.setReason(null);
        });

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(draftUpdateList);
        return invCountHeaders;
    }

    /**
     * filter list of inventory count headers that has specified count status
     * @param invCountHeaders list of inventory count headers
     * @param status status to find
     * @return inventory count header containing status from parameter
     */
    private List<InvCountHeader> filterHeaderListByStatus(List<InvCountHeader> invCountHeaders, String status) {
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
        Long currentUser = iamJSONObject.getLong(Constants.Iam.FIELD_ID);

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

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        List<Long> wmsWarehouseIds = getWMSWarehouseIds();
        InvCountHeader header = invCountHeaderRepository.selectByPrimary(countHeaderId);

        if (header == null) {
            throw new CommonException("InvCountHeader.notFound");
        }

        // map to dto
        InvCountHeaderDTO dto = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, dto);

        if (wmsWarehouseIds.contains(dto.getWarehouseId())) {
            dto.setIsWmsWarehouse(BaseConstants.Flag.YES);
        } else {
            dto.setIsWmsWarehouse(BaseConstants.Flag.NO);
        }

        List<UserInfoDTO> counterList = convertUserIdToList(dto.getCounterIds());
        List<UserInfoDTO> supervisorList = convertUserIdToList(dto.getSupervisorIds());
        List<MaterialInfoDTO> materialInfoDTOList = convertMaterialIdsToList(dto.getSnapshotMaterialIds());
        List<BatchInfoDTO> batchInfoDTOList = convertBatchIdsToList(dto.getSnapshotBatchIds());

        dto.setCounterList(counterList);
        dto.setSupervisorList(supervisorList);
        dto.setSnapshotBatchList(batchInfoDTOList);
        dto.setSnapshotMaterialList(materialInfoDTOList);
        return dto;
    }

    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        return null;
    }

    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        return Collections.emptyList();
    }

    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<Long> warehouseWMSIds = getWMSWarehouseIds();
        List<String> validUpdateStatuses = getValidCountStatus();

        String draftValue = Enums.InvCountHeader.Status.DRAFT.name();
        String inCountingValue = Enums.InvCountHeader.Status.INCOUNTING.name();
        String rejectedValue = Enums.InvCountHeader.Status.REJECTED.name();
        String withdrawnValue = Enums.InvCountHeader.Status.WITHDRAWN.name();

        List<String> validUpdateStatusSupervisorWMS = validUpdateStatuses
                .stream()
                .filter(status ->   status.equals(inCountingValue) ||
                                    status.equals(rejectedValue) ||
                                    status.equals(withdrawnValue))
                .collect(Collectors.toList());

        List<InvCountHeaderDTO> invalidHeaderDTOS = new ArrayList<>();
        List<InvCountHeaderDTO> validHeaderDTOS = new ArrayList<>();

        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSONObject.getLong(Constants.Iam.FIELD_ID);

        // business verifications
        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {
            List<Long> headerCounterIds = Utils.convertStringIdstoList(invCountHeaderDTO.getCounterIds());
            List<Long> supervisorIds = Utils.convertStringIdstoList(invCountHeaderDTO.getSupervisorIds());

            if (invCountHeaderDTO.getCountHeaderId() == null) {
                validHeaderDTOS.add(invCountHeaderDTO);
            } else if (!validUpdateStatuses.contains(invCountHeaderDTO.getCountStatus())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_STATUS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);

            } else if (draftValue.equals(invCountHeaderDTO.getCountStatus()) && userId.equals(invCountHeaderDTO.getCreatedBy())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_ACCESS_INVALID);
                invalidHeaderDTOS.add(invCountHeaderDTO);

            } else if (validUpdateStatusSupervisorWMS.contains(invCountHeaderDTO.getCountStatus())) {

                if (warehouseWMSIds.contains(invCountHeaderDTO.getWarehouseId()) && !supervisorIds.contains(userId)) {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.WAREHOUSE_SUPERVISOR_INVALID);
                    invalidHeaderDTOS.add(invCountHeaderDTO);
                }

                if (!headerCounterIds.contains(userId)&& !supervisorIds.contains(userId) &&
                        !invCountHeaderDTO.getCreatedBy().equals(userId)) {
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

    /**
     * generate invoice header number
     * @return invoice header number with format
     */
    private String generateCountNumber() {
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put(Constants.codeBuilder.FIELD_CUSTOM_SEGMENT, String.valueOf(BaseConstants.DEFAULT_TENANT_ID));
        return codeRuleBuilder.generateCode(Constants.InvCountHeader.CODE_RULE, variableMap);
    }

    /**
     * @return list of warehouse id that are part of WMS system
     */
    private List<Long> getWMSWarehouseIds(){
        InvWarehouse warehouse = new InvWarehouse();
        warehouse.setIsWmsWarehouse(BaseConstants.Flag.YES);
        return invWarehouseRepository
                .selectList(warehouse)
                .stream()
                .map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toList());
    }

    private List<String> getValidCountStatus() {
        return lovAdapter.queryLovValue(Constants.InvCountHeader.STATUS_LOV_CODE,BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private UserInfoDTO createNewUserInfoDTO(Long id) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(id);
        return dto;
    }

    private MaterialInfoDTO createNewMaterialInfoDTO(InvMaterial material) {
        MaterialInfoDTO dto = new MaterialInfoDTO();
        dto.setId(material.getMaterialId());
        dto.setCode(material.getMaterialCode());
        return dto;
    }

    private BatchInfoDTO createNewBatchInfoDTO(InvBatch batch) {
        BatchInfoDTO dto = new BatchInfoDTO();
        dto.setBatchId(batch.getBatchId());
        dto.setBatchCode(batch.getBatchCode());
        return dto;
    }

    private List<UserInfoDTO> convertUserIdToList(String userIds) {
        return Arrays.stream(userIds.split(","))
                .map(id -> createNewUserInfoDTO(Long.valueOf(id)))
                .collect(Collectors.toList());
    }

    private List<MaterialInfoDTO> convertMaterialIdsToList(String materialIds) {
        return invMaterialRepository.selectByIds(materialIds)
                .stream()
                .map(this::createNewMaterialInfoDTO)
                .collect(Collectors.toList());
    }

    private List<BatchInfoDTO> convertBatchIdsToList(String batchIds) {
        return invBatchRepository.selectByIds(batchIds)
                .stream()
                .map(this::createNewBatchInfoDTO)
                .collect(Collectors.toList());
    }

}

