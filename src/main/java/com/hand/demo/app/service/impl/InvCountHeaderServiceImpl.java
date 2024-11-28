package com.hand.demo.app.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvoiceCountHeaderConstant;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import com.hand.demo.app.service.InvCountHeaderService;
import org.hzero.mybatis.domian.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:15:49
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;
    @Autowired
    private LovAdapter lovAdapter;
    @Autowired
    private InvWarehouseRepository invWarehouseRepository;
    @Autowired
    private CodeRuleBuilder codeRuleBuilder;
    @Autowired
    private InvCountLineService lineService;
    @Autowired
    private InvCountLineRepository lineRepository;
    @Autowired
    private InvWarehouseRepository warehouseRepository;
    @Autowired
    private InvMaterialRepository materialRepository;
    @Autowired
    private InvBatchRepository batchRepository;

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId){
        InvCountHeaderDTO invCountHeaderDTO = invCountHeaderRepository.selectByPrimary(countHeaderId);

        //Create listCounter and listSupervisor
        List<IamUserDTO> counterList = createListUser(invCountHeaderDTO.getCounterIds());
        List<IamUserDTO> supervisorList = createListUser(invCountHeaderDTO.getSupervisorIds());

        //MaterialList
        List<InvMaterial> materialList = materialRepository.selectByIds(invCountHeaderDTO.getSnapshotMaterialIds());
        List<SnapshotMaterialDTO> snapshotMaterialDTOList = new ArrayList<>();
        for (InvMaterial material : materialList){
            SnapshotMaterialDTO materialDTO = new SnapshotMaterialDTO();
            materialDTO.setId(material.getMaterialId());
            materialDTO.setMaterialCode(material.getMaterialCode());
            snapshotMaterialDTOList.add(materialDTO);
        }

        //Batch List
        List<InvBatch> batchList = batchRepository.selectByIds(invCountHeaderDTO.getSnapshotBatchIds());
        List<SnapshotBatchDTO> snapshotBatchDTOS = new ArrayList<>();
        for (InvBatch batch : batchList){
            SnapshotBatchDTO batchDTO = new SnapshotBatchDTO();
            batchDTO.setBatchId(batch.getBatchId());
            batchDTO.setBatchCode(batch.getBatchCode());
            snapshotBatchDTOS.add(batchDTO);
        }

        //Create listLine
        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo(InvCountLine.FIELD_COUNT_HEADER_ID, countHeaderId);
        List<InvCountLine> lineList = lineRepository.selectByCondition(condition);

        //Query is WMS
        Integer isWMS = warehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId()).getIsWmsWarehouse();


        //Set to DTO
        invCountHeaderDTO.setCounterList(counterList);
        invCountHeaderDTO.setSupervisorList(supervisorList);
        invCountHeaderDTO.setSnapshotMaterialList(snapshotMaterialDTOList);
        invCountHeaderDTO.setSnapshotBatchList(snapshotBatchDTOS);
        invCountHeaderDTO.setInvCountLineDTOList(lineList);
        invCountHeaderDTO.setIsWmsWarehouse(isWMS);

        return invCountHeaderDTO;
    }

    private List<IamUserDTO> createListUser(String ids){
        List<IamUserDTO> listUser = new ArrayList<>();
        for (String id : ids.split(",")) {
            Long userId = Long.valueOf(id.trim());
            IamUserDTO userDTO = new IamUserDTO();
            userDTO.setId(userId);
            listUser.add(userDTO);
        }
        return listUser;
    }

    @Override
    public void manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        //Custom Segment for CodeRule
        Map<String, String> variableMap = new HashMap<>();
        Long tenantId = (BaseConstants.DEFAULT_TENANT_ID);
        String tenantIdString = tenantId.toString();
        variableMap.put("customSegment", (tenantIdString));

        //Collect request to insert or update
        List<InvCountHeaderDTO> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        for (InvCountHeaderDTO dto : insertList){
            dto.setTenantId(tenantId);
            String countNumberBuilder = codeRuleBuilder.generateCode(InvoiceCountHeaderConstant.CodeRule.CODE_RULE_HEADER_NUMBER, variableMap);
            dto.setCountStatus("DRAFT");
            dto.setCountNumber(countNumberBuilder);
            dto.setDelFlag(0);
        }

        //Update according Status
        List<InvCountHeaderDTO> updateForDraftStatus = new ArrayList<>();
        List<InvCountHeaderDTO> updateRemark = new ArrayList<>();
        List<InvCountHeaderDTO> updateReason = new ArrayList<>();
        for (InvCountHeaderDTO dto : updateList){
            if(dto.getCountStatus().equals("DRAFT")){
                if(!dto.getReason().isEmpty()){
                    dto.setReason(null);
                }
                updateForDraftStatus.add(dto);
            }
            if(dto.getCountStatus().equals("DRAFT") || dto.getCountStatus().equals("INCOUNTING")){
                if(!dto.getRemark().isEmpty()){
                    updateRemark.add(dto);
                }
            }
            if(dto.getCountStatus().equals("INCOUNTING") || dto.getCountStatus().equals("REJECTED")){
                if(!dto.getReason().isEmpty()){
                    updateReason.add(dto);
                }
            }
        }

        if (CollUtil.isNotEmpty(insertList)){
            invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
            saveLines(insertList);
        }
        if (CollUtil.isNotEmpty(updateForDraftStatus)){
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateForDraftStatus));
            saveLines(updateForDraftStatus);
        }
        if (CollUtil.isNotEmpty(updateRemark)){
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateRemark), InvCountHeader.FIELD_REMARK);
            saveLines(updateRemark);
        }
        if (CollUtil.isNotEmpty(updateReason)){
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateReason), InvCountHeader.FIELD_REASON);
            saveLines(updateReason);
        }
    }

    @Override
    public void checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<String> errorList = new ArrayList<>();
        int requestNumber = 1;
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS){
            if (!headerDTO.getCountStatus().equals("DRAFT")){
                String errorMsg = "Request " + requestNumber + " error, only Draft status can deleted";
                errorList.add(errorMsg);
            }
            if (headerDTO.getCreatedBy() != currentUserId){
                String errorMsg = "Request " + requestNumber + " error, this createdBy not matches with currentUser";
                errorList.add(errorMsg);
            }
        }

        if (CollUtil.isNotEmpty(errorList)){
            String errorMsg = String.join("|", errorList);
            throw new CommonException(errorMsg);
        }

        invCountHeaderRepository.batchDeleteByPrimaryKey(new ArrayList<>(invCountHeaderDTOS));
    }

    @Override
    public void manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
        //For collect Error
        List<String> errorList = new ArrayList<>();

        //Allowed ListValue
        List<String> allowedCountStatusUpdate = Arrays.asList("DRAFT", "INCOUNTING", "REJECTED", "WITHDRAWN");

        //Get Current User
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        //Create update list for validate
        List<InvCountHeaderDTO> updateList = new ArrayList<>();
        for (InvCountHeaderDTO countHeaderDTO : invCountHeaders) {
            if (countHeaderDTO.getCountHeaderId() != null) {
                updateList.add(countHeaderDTO);
            }
        }

        //Cancel Validation if updateList is null or empty
        if(updateList.isEmpty()){
            return;
        }

        //Get Verification Data for update
        Set<Long> countHeaderIds = new HashSet<>();
        for (InvCountHeaderDTO dto : updateList) {
            countHeaderIds.add(dto.getCountHeaderId());
        }

        String countHeaderIdsString = countHeaderIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        List<InvCountHeader> existingHeader = invCountHeaderRepository.selectByIds(countHeaderIdsString);

        //Get Existing WareHouse from Existing Header
        Set<Long> wareHouseIds = new HashSet<>();
        for (InvCountHeader header : existingHeader) {
            wareHouseIds.add(header.getWarehouseId());
        }
        String wareHouseIdsString = wareHouseIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<InvWarehouse> existingWareHouse = invWarehouseRepository.selectByIds(wareHouseIdsString);

        Map<Long, InvWarehouse> warehouseMap = new HashMap<>();
        for (InvWarehouse warehouse : existingWareHouse) {
            warehouseMap.put(warehouse.getWarehouseId(), warehouse);
        }

        //Validation for Update
        for (InvCountHeader header : existingHeader) {
            //Judge whether the document status is "Draft, In Counting, Rejected, Withdrawn" status
            if (!allowedCountStatusUpdate.contains(header.getCountStatus())) {
                String errorMsg = "Update headerId " + header.getCountHeaderId() + "error: only draft, in counting, rejected, and withdrawn status can be modified";
                errorList.add(errorMsg);
            }

            //Judge the current user
            if (header.getCountStatus().equals("DRAFT") && !header.getCreatedBy().equals(currentUserId)) {
                String errorMsg = "Update headerId " + header.getCountHeaderId() + "error: Document in draft status can only be modified by the document creator";
                errorList.add(errorMsg);
            }

            //Get Warehouse Data
            InvWarehouse warehouse = warehouseMap.get(header.getWarehouseId());

            //Get listSupervisor for check
            String supervisorIdsString = header.getSupervisorIds();
            List<Long> supervisorIds = Arrays.stream(supervisorIdsString.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            //Get listCounter for check
            String counterIdsString = header.getCounterIds();
            List<Long> counterIds = Arrays.stream(counterIdsString.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            if (allowedCountStatusUpdate.contains(header.getCountStatus()) && !header.getCountStatus().equals("DRAFT")) {
                if (warehouse.getIsWmsWarehouse() == (1) && !supervisorIds.contains(currentUserId)) {
                    String errorMsg = "Update headerId " + header.getCountHeaderId() + "error: The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate";
                    errorList.add(errorMsg);
                }
                if (!counterIds.contains(currentUserId) || !supervisorIds.contains(currentUserId) || !header.getCreatedBy().equals(currentUserId)) {
                    String errorMsg = "Update headerId " + header.getCountHeaderId() + "error: only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn";
                    errorList.add(errorMsg);
                }
            }
        }

        if (!errorList.isEmpty()) {
            // Join all elements of errorList with "|" delimiter
            String errorMsg = String.join("|", errorList);

            // Create the DTO and set the errorMsg
            InvCountInfoDTO infoDTO = new InvCountInfoDTO();
            infoDTO.setErrorMsg(errorMsg);

            throw new CommonException(infoDTO.getErrorMsg());
        }
    }

    private List<String> getValidLovValues(String lovCode) {
        return lovAdapter
                .queryLovValue(lovCode, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    public void executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS){


    }
    private void saveLines(List<InvCountHeaderDTO> listHeaderDTOSaved){
        for (InvCountHeaderDTO headerDTO : listHeaderDTOSaved) {
            List<InvCountLine> lines = headerDTO.getInvCountLineDTOList();
            if (CollUtil.isEmpty(lines)) {
                continue;
            }
            for (InvCountLine line : lines) {
                //Set headerId from the header
                line.setCountHeaderId(headerDTO.getCountHeaderId());
                if(line.getCountLineId() == null){
                    line.setCounterIds(headerDTO.getCounterIds());
                }
            }
            lineService.saveData(lines);
        }
    }

    private void validateCountValueSet(int lineNumber, InvCountHeaderDTO countHeaderDTO,
                                    List<String> validCountDimensions, List<String> validCountTypes, List<String> validCountModes) {
        if (!validCountDimensions.contains(countHeaderDTO.getCountDimension())) {
            countHeaderDTO.setErrorMsg("Line " + lineNumber + " Dimension just allow SKU & LOT");
        }
        if (!validCountTypes.contains(countHeaderDTO.getCountType())) {
            countHeaderDTO.setErrorMsg("Line " + lineNumber + " Type just allow MONTH & YEAR");
        }
        if (!validCountModes.contains(countHeaderDTO.getCountMode())) {
            countHeaderDTO.setErrorMsg("Line " + lineNumber + " Mode just allow VISIBLE_COUNT & UNVISIBLE_COUNT");
        }
    }
}

