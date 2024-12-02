package com.hand.demo.app.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private InvCountLineRepository invCountLineRepository;

    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Autowired
    private InvMaterialRepository invMaterialRepository;

    @Autowired
    private InvBatchRepository invBatchRepository;

    @Autowired
    private IamCompanyRepository iamCompanyRepository;

    @Autowired
    private IamDepartmentRepository iamDepartmentRepository;

    @Autowired
    private InvStockRepository invStockRepository;

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
        Long currentUser = DetailsHelper.getUserDetails().getUserId();

        Set<String> modifyStatuses = new HashSet<>(Arrays.asList(Constants.COUNT_DRAFT_STATUS, Constants.COUNT_INCOUNTING_STATUS, Constants.COUNT_REJECTED_STATUS, Constants.COUNT_WITHDRAWN_STATUS));

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

            if (Constants.COUNT_DRAFT_STATUS.equals(dto.getCountStatus()) && !dto.getCreatedBy().equals(currentUser)) {
                individualErrorMsgs.append("Document in draft status can only be modified by the document creator for CountHeaderId ")
                        .append(dto.getCountHeaderId()).append(".\n");
            }

            // Validation for inactive statuses
            if (modifyStatuses.contains(dto.getCountStatus())) {
                InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimaryKey(dto.getWarehouseId());

                if (invWarehouse.getIsWmsWarehouse().equals(1) && !dto.getSupervisorIds().contains(currentUser.toString())) {
                    individualErrorMsgs.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate for CountHeaderId ")
                            .append(dto.getCountHeaderId()).append(".\n");
                }

                List<String> counterIdsList = new ArrayList<>(Arrays.asList(dto.getCounterIds().split(","))); // Assuming comma-separated
                List<String> supervisorIdsList = new ArrayList<>(Arrays.asList(dto.getSupervisorIds().split(","))); // Assuming comma-separated

                if (!(counterIdsList.contains(currentUser.toString()) || supervisorIdsList.contains(currentUser.toString()) || dto.getCreatedBy().equals(currentUser))) {
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

        // LovValue validation
        List<String> validCountDimension = lovAdapter.queryLovValue(Constants.LovCode.COUNT_DIMENSION, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> validCountType = lovAdapter.queryLovValue(Constants.LovCode.COUNT_TYPE, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> validCountMode = lovAdapter.queryLovValue(Constants.LovCode.COUNT_MODE, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());

        for (int i = 0; i < invCountHeaders.size(); i++) {
            InvCountHeaderDTO dto = invCountHeaders.get(i);

            if (!validCountDimension.contains(dto.getCountDimension())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Count Dimension: " + dto.getCountDimension());
            }
            if (!validCountType.contains(dto.getCountType())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Count Type: " + dto.getCountType());
            }
            if (!validCountMode.contains(dto.getCountMode())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Count Mode: " + dto.getCountMode());
            }
        }

        if (!invalidHeaders.isEmpty()) {
            throw new CommonException("Invalid Count Headers: " + String.join(", ", invalidHeaders));
        }

        // Validate the headers with error msgs
        for (InvCountHeaderDTO dto : invCountHeaders) {
            if (dto.getInvCountHeaderErrorMsg() != null) {
                return;
            }
        }

        // Build code and set default value
        invCountHeaders.forEach(header -> {
            if (header.getCountHeaderId() == null) {
                Map<String, String> variableMap = new HashMap<>();
                variableMap.put("customSegment", ("-" + DetailsHelper.getUserDetails().getTenantId().toString() + "-"));
                String uniqueCode = codeRuleBuilder.generateCode(Constants.CODE_RULE, variableMap);
                header.setCountNumber(uniqueCode);
                header.setCountStatus(Constants.COUNT_DRAFT_STATUS);
                header.setDelFlag(0);
            }
        });

        // Update data
        List<InvCountHeader> updateList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() != null)
                .collect(Collectors.toList());

        updateList.forEach(header -> {

            if (Constants.COUNT_DRAFT_STATUS.equals(header.getCountStatus())) {
                header.setCountDimension(header.getCountDimension());
                header.setCountType(header.getCountType());
                header.setCountMode(header.getCountMode());
                header.setCountTimeStr(header.getCountTimeStr());
                header.setCounterIds(header.getCounterIds());
                header.setSupervisorIds(header.getSupervisorIds());
                header.setSnapshotMaterialIds(header.getSnapshotMaterialIds());
                header.setSnapshotBatchIds(header.getSnapshotBatchIds());
            }

            if (Constants.COUNT_DRAFT_STATUS.equals(header.getCountStatus()) || Constants.COUNT_INCOUNTING_STATUS.equals(header.getCountStatus())) {
                header.setRemark(header.getRemark());
            }

            if (Constants.COUNT_INCOUNTING_STATUS.equals(header.getCountStatus()) || Constants.COUNT_REJECTED_STATUS.equals(header.getCountStatus())) {
                header.setReason(header.getReason());
            }
        });

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);

        // Insert data
        List<InvCountHeader> insertList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() == null)
                .collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);
    }


    @Override
    public void remove(List<InvCountHeaderDTO> invCountHeaders) {

        List<String> errorMessages = new ArrayList<>();

        this.checkRemove(invCountHeaders, errorMessages);

        if (!errorMessages.isEmpty()) {

            // Collect all error messages and throw
            throw new CommonException(String.join("\n", errorMessages));
        }

        // Delete data
        List<InvCountHeader> deleteList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() != null && line.getCountStatus().equals(Constants.COUNT_DRAFT_STATUS))
                .collect(Collectors.toList());
        invCountHeaderRepository.batchDelete(deleteList);
    }

    private void checkRemove(List<InvCountHeaderDTO> invCountHeaders, List<String> errorMessages) {

        for (int i = 0; i < invCountHeaders.size(); i++) {
            InvCountHeaderDTO dto = invCountHeaders.get(i);

            // status verification, Only allow draft status to be deleted
            if (!dto.getCountStatus().equals(Constants.COUNT_DRAFT_STATUS)) {
                errorMessages.add("Error at index " + i + ": Only documents with draft status can be deleted.");
            }

            // current user verification, Only current user is document creator allowed to delete document
            if (!DetailsHelper.getUserDetails().getUserId().equals(dto.getCreatedBy())) {
                errorMessages.add("Error at index " + i + ": Only the document creator can delete this.");
            }
        }
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {

        InvCountHeader header = invCountHeaderRepository.selectByPrimaryKey(countHeaderId);
        if (header == null) {
            throw new CommonException("Count header not found for ID: " + countHeaderId);
        }

        InvCountHeaderDTO dto = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, dto);

        InvCountLineDTO lineCriteria = new InvCountLineDTO();
        lineCriteria.setCountHeaderId(countHeaderId);
        List<InvCountLineDTO> countLineList = invCountLineRepository.selectList(lineCriteria);
        dto.setInvCountLineList(countLineList);

        List<MaterialDTO> materialDTOS = invMaterialRepository.selectByIds(dto.getSnapshotMaterialIds())
                .stream()
                .map(material -> {
                    MaterialDTO materialDTO = new MaterialDTO();
                    materialDTO.setId(material.getMaterialId());
                    materialDTO.setMaterialCode(material.getMaterialCode());
                    return materialDTO;
                })
                .collect(Collectors.toList());

        dto.setSnapshotMaterialList(materialDTOS);

        List<BatchDTO> invBatchList = invBatchRepository.selectByIds(dto.getSnapshotBatchIds())
                .stream()
                .map(material -> {
                    BatchDTO batchDTO = new BatchDTO();
                    batchDTO.setId(material.getBatchId());
                    batchDTO.setBatchCode(material.getBatchCode());
                    return batchDTO;
                })
                .collect(Collectors.toList());
        dto.setSnapshotBatchList(invBatchList);

        int isWMS = invWarehouseRepository.selectByPrimaryKey(dto.getWarehouseId()).getIsWmsWarehouse();
        dto.setIsWMSwarehouse(isWMS);

        List<IamUserDTO> supervisorList = Arrays.stream(dto.getSupervisorIds().split(","))
                .map(Long::parseLong)
                .map(id -> {
                    IamUserDTO supervisor = new IamUserDTO();
                    supervisor.setId(id);
                    return supervisor;
                })
                .collect(Collectors.toList());
        dto.setSupervisorList(supervisorList);

        List<IamUserDTO> counterList = Arrays.stream(dto.getCounterIds().split(","))
                .map(Long::parseLong)
                .map(id -> {
                    IamUserDTO counter = new IamUserDTO();
                    counter.setId(id);
                    return counter;
                })
                .collect(Collectors.toList());
        dto.setCounterList(counterList);

        return dto;
    }

    @Override
    public void execute(List<InvCountHeaderDTO> invCountHeaders) {

        this.executeOrderCheck(invCountHeaders);

        this.executeOrder(invCountHeaders);
    }

    private void executeOrder(List<InvCountHeaderDTO> invCountHeaders) {

        List<InvStock> stocks = invStockRepository.selectList(new InvStock());
        Map<String, List<InvStock>> stockMap = new HashMap<>();

        for (InvStock stock : stocks) {
            long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
            String key = (stock.getTenantId() != 0L)
                    ? stock.getTenantId() + stock.getCompanyId().toString() + departmentId + stock.getWarehouseId().toString() + stock.getMaterialId().toString()
                    : stock.getCompanyId().toString() + departmentId + stock.getWarehouseId().toString() + stock.getMaterialId().toString();
            stockMap.computeIfAbsent(key, k -> new ArrayList<>()).add(stock);
        }

        for (InvCountHeaderDTO headerDTO : invCountHeaders) {
            headerDTO.setCountStatus("INCOUNTING");

            long tenantId = (headerDTO.getTenantId() != null) ? headerDTO.getTenantId() : 0L;
            String[] materialIds = headerDTO.getSnapshotMaterialIds().split(",");
            String keyBase = (tenantId != 0L)
                    ? tenantId + headerDTO.getCompanyId().toString() + headerDTO.getDepartmentId().toString() + headerDTO.getWarehouseId().toString()
                    : headerDTO.getCompanyId().toString() + headerDTO.getDepartmentId().toString() + headerDTO.getWarehouseId().toString();

            List<InvCountLineDTO> lineList = new ArrayList<>();

            if (headerDTO.getCountDimension().equals("SKU")) {

                for (String materialId : materialIds) {
                    BigDecimal snapUnitQty = BigDecimal.ZERO;
                    String unitCode = "";
                    Long batchId = null;

                    for (InvStock stock : stockMap.get(keyBase + materialId)) {
                        if (materialId.equals(stock.getMaterialId().toString())) {
                            snapUnitQty = snapUnitQty.add(stock.getUnitQuantity());
                            unitCode = stock.getUnitCode();
                            batchId = stock.getBatchId();
                        }
                    }

                    InvCountLineDTO line = new InvCountLineDTO();
                    line.setTenantId(headerDTO.getTenantId());
                    line.setCountHeaderId(headerDTO.getCountHeaderId());
                    line.setWarehouseId(headerDTO.getWarehouseId());
                    line.setMaterialId(Long.parseLong(materialId));
                    line.setUnitCode(unitCode);
                    line.setSnapshotUnitQty(snapUnitQty);
                    line.setCounterIds(headerDTO.getCounterIds());
                    line.setBatchId(batchId);

                    lineList.add(line);
                }
            } else if (headerDTO.getCountDimension().equals("LOT")) {

                for (String materialId : materialIds) {
                    for (InvStock stock : stockMap.get(keyBase + materialId)) {
                        BigDecimal snapUnitQty = BigDecimal.ZERO;
                        String unitCode = "";

                        // Process based on batch ID
                        for (String batchId : headerDTO.getSnapshotBatchIds().split(",")) {
                            Long batchIdLong = Long.parseLong(batchId);
                            if (materialId.equals(stock.getMaterialId().toString()) && batchIdLong.equals(stock.getBatchId())) {
                                snapUnitQty = snapUnitQty.add(stock.getUnitQuantity());
                                unitCode = stock.getUnitCode();
                            }

                            InvCountLineDTO line = new InvCountLineDTO();
                            line.setTenantId(headerDTO.getTenantId());
                            line.setCountHeaderId(headerDTO.getCountHeaderId());
                            line.setWarehouseId(headerDTO.getWarehouseId());
                            line.setMaterialId(Long.parseLong(materialId));
                            line.setUnitCode(unitCode);
                            line.setSnapshotUnitQty(snapUnitQty);
                            line.setCounterIds(headerDTO.getCounterIds());
                            line.setBatchId(batchIdLong);

                            lineList.add(line);
                        }
                    }
                }
            }

            headerDTO.setInvCountLineList(lineList);
        }

        invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(invCountHeaders), InvCountHeader.FIELD_COUNT_STATUS);
        saveLines(invCountHeaders);
    }

    private void saveLines(List<InvCountHeaderDTO> listHeaderDTOSaved) {
        List<InvCountLineDTO> insertLines = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : listHeaderDTOSaved) {
            List<InvCountLineDTO> lines = headerDTO.getInvCountLineList();
            if (CollUtil.isEmpty(lines)) {
                continue;
            }
            insertLines.addAll(lines);
        }
        invCountLineService.saveData(insertLines);
    }

    private void executeOrderCheck(List<InvCountHeaderDTO> invCountHeaders) {

        List<String> invalidHeaders = new ArrayList<>();

        List<String> validCountDimension = lovAdapter.queryLovValue(Constants.LovCode.COUNT_DIMENSION, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> validCountType = lovAdapter.queryLovValue(Constants.LovCode.COUNT_TYPE, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> validCountMode = lovAdapter.queryLovValue(Constants.LovCode.COUNT_MODE, BaseConstants.DEFAULT_TENANT_ID)
                .stream().map(LovValueDTO::getValue).collect(Collectors.toList());

        List<Long> validCompanyIds = iamCompanyRepository.selectList(new IamCompany()).stream().map(IamCompany::getCompanyId).collect(Collectors.toList());
        List<Long> validDepartmentIds = iamDepartmentRepository.selectList(new IamDepartment()).stream().map(IamDepartment::getDepartmentId).collect(Collectors.toList());
        List<Long> validWarehouseIds = invWarehouseRepository.selectList(new InvWarehouse()).stream().map(InvWarehouse::getWarehouseId).collect(Collectors.toList());

        for (int i = 0; i < invCountHeaders.size(); i++) {
            InvCountHeaderDTO header = invCountHeaders.get(i);

            if (!header.getCountStatus().equals(Constants.COUNT_DRAFT_STATUS) && !DetailsHelper.getUserDetails().getUserId().equals(header.getCreatedBy())) {
                continue;
            }

            if (!validCountDimension.contains(header.getCountDimension())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Count Dimension: " + header.getCountDimension());
            }
            if (!validCountType.contains(header.getCountType())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Count Type: " + header.getCountType());
            }
            if (!validCountMode.contains(header.getCountMode())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Count Mode: " + header.getCountMode());
            }

            if (!validCompanyIds.contains(header.getCompanyId())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Company ID: " + header.getCompanyId());
            }
            if (!validDepartmentIds.contains(header.getDepartmentId())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Department ID: " + header.getDepartmentId());
            }
            if (!validWarehouseIds.contains(header.getWarehouseId())) {
                invalidHeaders.add("Line No " + (i + 1) + " - Invalid Warehouse ID: " + header.getWarehouseId());
            }
        }

        if (!invalidHeaders.isEmpty()) {
            throw new CommonException("Invalid Count Headers: " + String.join(", ", invalidHeaders));
        }

        List<InvStock> stocks = invStockRepository.selectList(new InvStock());
        Map<String, InvStock> stockMap = new HashMap<>();

        for (InvStock stock : stocks) {
            long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
            String key = (stock.getTenantId() != 0L)
                    ? stock.getTenantId() + stock.getCompanyId().toString() + departmentId + stock.getWarehouseId().toString() + stock.getMaterialId().toString()
                    : stock.getCompanyId().toString() + departmentId + stock.getWarehouseId().toString() + stock.getMaterialId().toString();
            stockMap.put(key, stock);
        }

        for (InvCountHeaderDTO header : invCountHeaders) {
            long tenantId = (header.getTenantId() != null) ? header.getTenantId() : 0L;

            String[] materialIds = header.getSnapshotMaterialIds().split(",");

            for (String materialId : materialIds) {

                String key = (tenantId != 0L)
                        ? tenantId + header.getCompanyId().toString() + header.getDepartmentId().toString() + header.getWarehouseId().toString() + materialId
                        : header.getCompanyId().toString() + header.getDepartmentId().toString() + header.getWarehouseId().toString() + materialId;

                InvStock stock = stockMap.get(key);
                if (stock == null || stock.getUnitQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    String errorMsg = "HeaderId " + header.getCountHeaderId() + " error: Unable to query on hand quantity data for materialId " + materialId;
                    invalidHeaders.add(errorMsg);
                }
            }
        }
        
        if (!invalidHeaders.isEmpty()) {
            throw new CommonException("Invalid Count Headers: " + String.join(", ", invalidHeaders));
        }
    }

}

