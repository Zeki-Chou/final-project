package com.hand.demo.app.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
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
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import com.hand.demo.app.service.InvCountHeaderService;
import org.hzero.mybatis.domian.Condition;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    @Autowired
    private IamCompanyRepository companyRepository;
    @Autowired
    private IamDepartmentRepository departmentRepository;
    @Autowired
    private InvStockRepository stockRepository;
    @Autowired
    private InvCountExtraRepository extraRepository;
    @Autowired
    private InterfaceInvokeSdk interfaceInvokeSdk;

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        InvCountHeaderDTO invCountHeaderDTO = invCountHeaderRepository.selectByPrimary(countHeaderId);

        //Create listCounter and listSupervisor
        List<IamUserDTO> counterList = createListUser(invCountHeaderDTO.getCounterIds());
        List<IamUserDTO> supervisorList = createListUser(invCountHeaderDTO.getSupervisorIds());

        //MaterialList
        List<InvMaterial> materialList = materialRepository.selectByIds(invCountHeaderDTO.getSnapshotMaterialIds());
        List<SnapshotMaterialDTO> snapshotMaterialDTOList = new ArrayList<>();
        for (InvMaterial material : materialList) {
            SnapshotMaterialDTO materialDTO = new SnapshotMaterialDTO();
            materialDTO.setId(material.getMaterialId());
            materialDTO.setMaterialCode(material.getMaterialCode());
            snapshotMaterialDTOList.add(materialDTO);
        }

        //Batch List
        List<InvBatch> batchList = batchRepository.selectByIds(invCountHeaderDTO.getSnapshotBatchIds());
        List<SnapshotBatchDTO> snapshotBatchDTOS = new ArrayList<>();
        for (InvBatch batch : batchList) {
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

    private List<IamUserDTO> createListUser(String ids) {
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

        for (InvCountHeaderDTO dto : insertList) {
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
        for (InvCountHeaderDTO dto : updateList) {
            if (dto.getCountStatus().equals("DRAFT")) {
                if (!dto.getReason().isEmpty()) {
                    dto.setReason(null);
                }
                updateForDraftStatus.add(dto);
            }
            if (dto.getCountStatus().equals("DRAFT") || dto.getCountStatus().equals("INCOUNTING")) {
                if (!dto.getRemark().isEmpty()) {
                    updateRemark.add(dto);
                }
            }
            if (dto.getCountStatus().equals("INCOUNTING") || dto.getCountStatus().equals("REJECTED")) {
                if (!dto.getReason().isEmpty()) {
                    updateReason.add(dto);
                }
            }
        }

        if (CollUtil.isNotEmpty(insertList)) {
            invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
//            saveLines(insertList);
        }
        if (CollUtil.isNotEmpty(updateForDraftStatus)) {
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateForDraftStatus));
//            saveLines(updateForDraftStatus);
        }
        if (CollUtil.isNotEmpty(updateRemark)) {
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateRemark), InvCountHeader.FIELD_REMARK);
//            saveLines(updateRemark);
        }
        if (CollUtil.isNotEmpty(updateReason)) {
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateReason), InvCountHeader.FIELD_REASON);
//            saveLines(updateReason);
        }
    }

    @Override
    public void checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<String> errorList = new ArrayList<>();
        int requestNumber = 1;
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            if (!headerDTO.getCountStatus().equals("DRAFT")) {
                String errorMsg = "Request " + requestNumber + " error, only Draft status can deleted";
                errorList.add(errorMsg);
            }
            if (headerDTO.getCreatedBy() != currentUserId) {
                String errorMsg = "Request " + requestNumber + " error, this createdBy not matches with currentUser";
                errorList.add(errorMsg);
            }
        }

        if (CollUtil.isNotEmpty(errorList)) {
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
        if (updateList.isEmpty()) {
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

    @Override
    public void executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Requery based on headerId
        List<Long> requestHeaderIds = new ArrayList<>();
        for (InvCountHeaderDTO dto : invCountHeaderDTOS) {
            requestHeaderIds.add(dto.getCountHeaderId());
        }

        String countHeaderIdsString = requestHeaderIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<InvCountHeader> existingCountHeaders = invCountHeaderRepository.selectByIds(countHeaderIdsString);

        //Verification
        List<String> errorList = new ArrayList<>();
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();
        List<String> dimensionLoveValues = getValidLovValues(InvoiceCountHeaderConstant.LovCode.COUNT_DIMENSION);
        List<String> typeLoveValues = getValidLovValues(InvoiceCountHeaderConstant.LovCode.COUNT_TYPE);
        List<String> modeLoveValues = getValidLovValues(InvoiceCountHeaderConstant.LovCode.COUNT_MODE);

        List<Long> existingCompanyIds = companyRepository.selectList(new IamCompany())
                .stream().map(IamCompany::getCompanyId)
                .collect(Collectors.toList());
        List<Long> existingDepartmentIds = departmentRepository.selectList(new IamDepartment())
                .stream().map(IamDepartment::getDepartmentId)
                .collect(Collectors.toList());
        List<Long> existingWarehouseIds = warehouseRepository.selectList(new InvWarehouse())
                .stream().map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toList());

        //Query All stock and make key like concat of all the param to optimize query every loop
        List<InvStock> stocks = stockRepository.selectList(new InvStock());
        Map<String, InvStock> stockMap = new HashMap<>();

        for (InvStock stock : stocks) {
            Long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
            String key = (stock.getTenantId() != 0L)
                    ? stock.getTenantId().toString() + stock.getCompanyId().toString() + departmentId.toString() + stock.getWarehouseId().toString() + stock.getMaterialId().toString()
                    : stock.getCompanyId().toString() + departmentId.toString() + stock.getWarehouseId().toString() + stock.getMaterialId().toString();
            stockMap.put(key, stock);
        }


        for (InvCountHeader header : existingCountHeaders) {
            //Draft Status
            if (!header.getCountStatus().equals("DRAFT")) {
                String errorMsg = "Header Id " + header.getCountHeaderId() + " error: Only DRAFT status can execute";
                errorList.add(errorMsg);
            }

            //Creator verification
            if (!header.getCreatedBy().equals(currentUserId)) {
                String errorMsg = "Header Id " + header.getCountHeaderId() + " error: Only the document creator can execute ";
                errorList.add(errorMsg);
            }

            //Value Set verification
            validateCountValueSet(header, errorList, dimensionLoveValues, typeLoveValues, modeLoveValues);

            //Company, Department and Warehouse verification
            if (!existingCompanyIds.contains(header.getCompanyId()) && !existingDepartmentIds.contains(header.getDepartmentId()) && !existingWarehouseIds.contains(header.getWarehouseId())) {
                String errorMsg = "Header Id " + header.getCountHeaderId() + " error: Only existing company, department, and warehouse can be executed";
                errorList.add(errorMsg);
            }

            //Check Stock by company, department, warehouse and material
            List<String> materialIds = Arrays.stream(header.getSnapshotMaterialIds().split(","))
                    .collect(Collectors.toList());
            for (String materialId : materialIds) {
                String key = (header.getTenantId() != 0L)
                        ? header.getTenantId().toString() + header.getCompanyId().toString() + header.getDepartmentId().toString() + header.getWarehouseId().toString() + materialId
                        : header.getCompanyId().toString() + header.getDepartmentId().toString() + header.getWarehouseId().toString() + materialId;
                InvStock stock = stockMap.get(key);
                if (stock == null || stock.getUnitQuantity().equals(BigDecimal.ZERO)) {
                    String errorMsg = "HeaderId " + header.getCountHeaderId() + " error: Unable to query on hand quantity data.";
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

    public void execute(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Query All stock and make key concat of all the param to optimize query every loop
        List<InvStock> stocks = stockRepository.selectList(new InvStock());
        Map<String, List<InvStock>> stockMap = new HashMap<>();

        for (InvStock stock : stocks) {
            Long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
            String key = (stock.getTenantId() != 0L)
                    ? stock.getTenantId().toString() + stock.getCompanyId().toString() + departmentId.toString() + stock.getWarehouseId().toString()
                    : stock.getCompanyId().toString() + departmentId.toString() + stock.getWarehouseId().toString();

            // Jika key sudah ada, tambahkan stock ke dalam list yang sudah ada
            stockMap.computeIfAbsent(key, k -> new ArrayList<>()).add(stock);
        }

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            headerDTO.setCountStatus("INCOUNTING");

            //Query the Stock from param in header
            String key = (headerDTO.getTenantId() != 0L)
                    ? headerDTO.getTenantId().toString() + headerDTO.getCompanyId().toString() + headerDTO.getDepartmentId().toString() + headerDTO.getWarehouseId().toString()
                    : headerDTO.getCompanyId().toString() + headerDTO.getDepartmentId().toString() + headerDTO.getWarehouseId().toString();
            List<InvStock> relatedStocks = stockMap.get(key);

            List<Long> listMaterialId = Arrays.stream(headerDTO.getSnapshotMaterialIds().split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            List<Long> listBatchId = Arrays.stream(headerDTO.getSnapshotBatchIds().split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            //Conditioning by dimension
            List<InvCountLine> lineList = new ArrayList<>();

            if (headerDTO.getCountDimension().equals("SKU")) {
                //SKU = Summarize by materialId and save in line
                for (Long materialId : listMaterialId) {
                    BigDecimal snapUnitQty = BigDecimal.ZERO;
                    String unitCode = "";
                    for (InvStock stock : relatedStocks) {
                        if (materialId == stock.getMaterialId()) {
                            snapUnitQty = snapUnitQty.add(stock.getUnitQuantity());
                            unitCode = stock.getUnitCode();
                        }
                    }

                    InvCountLine line = new InvCountLine();
                    line.setTenantId(BaseConstants.DEFAULT_TENANT_ID);
                    line.setCountHeaderId(headerDTO.getCountHeaderId());
                    line.setWarehouseId(headerDTO.getWarehouseId());
                    line.setMaterialId(materialId);
                    line.setUnitCode(unitCode);
                    line.setSnapshotUnitQty(snapUnitQty);
                    line.setCounterIds(headerDTO.getCounterIds());

                    lineList.add(line);
                }
            }

            if (headerDTO.getCountDimension().equals("LOT")) {
                //LOT = Summarize by materialId and batchId, then save in line
                for (Long materialId : listMaterialId) {
                    BigDecimal snapUnitQty = BigDecimal.ZERO;
                    String unitCode = "";
                    for (Long batchId : listBatchId) {
                        for (InvStock stock : relatedStocks) {
                            if (materialId == stock.getMaterialId() && batchId == stock.getBatchId()) {
                                snapUnitQty = snapUnitQty.add(stock.getUnitQuantity());
                                unitCode = stock.getUnitCode();
                            }
                        }
                    }

                    InvCountLine line = new InvCountLine();
                    line.setTenantId(BaseConstants.DEFAULT_TENANT_ID);
                    line.setCountHeaderId(headerDTO.getCountHeaderId());
                    line.setWarehouseId(headerDTO.getWarehouseId());
                    line.setMaterialId(materialId);
                    line.setUnitCode(unitCode);
                    line.setSnapshotUnitQty(snapUnitQty);
                    line.setCounterIds(headerDTO.getCounterIds());

                    lineList.add(line);
                }
            }
            headerDTO.setInvCountLineDTOList(lineList);
        }
        saveLines(invCountHeaderDTOS);
    }

    public void countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOS){
        //Query All to optimize query looping
        List<InvWarehouse> warehouseList = invWarehouseRepository.selectList(new InvWarehouse());
        Map<Long, InvWarehouse> warehouseMap = warehouseList.stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, warehouse -> warehouse));

        List<Long> headerIdList = invCountHeaderDTOS.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .collect(Collectors.toList());

        Condition conditionLine = new Condition(InvCountLine.class);
        Condition.Criteria criteriaLine = conditionLine.createCriteria();
        criteriaLine.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIdList );
        List<InvCountLine> lineList = lineRepository.selectByCondition(conditionLine);
        Map<Long, List<InvCountLine>> lineMap = lineList.stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));

        Condition conditionExtra = new Condition(InvCountExtra.class);
        Condition.Criteria criteriaExtra = conditionExtra.createCriteria();
        criteriaExtra.andEqualTo(InvCountExtra.FIELD_ENABLEDFLAG, 1);
        List<InvCountExtra> extraList = extraRepository.selectByCondition(conditionExtra);
        Map<Long, List<InvCountExtra>> extraMap = extraList.stream()
                .collect(Collectors.groupingBy(InvCountExtra::getSourceid));

        List<String> errorList = new ArrayList<>();

        //Query warehouse data based on warehouseId
        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS){
            InvWarehouse warehouse = warehouseMap.get(headerDTO.getWarehouseId());
            if (warehouse == null){
                String errorMsg = "Header Id " + headerDTO.getCountHeaderId() + " error: Warehouse is not Found";
                errorList.add(errorMsg);
            }

            Map<String, InvCountExtra> mapOldExtras = extraMap.get(headerDTO.getCountHeaderId())
                    .stream()
                    .collect(Collectors.toMap(InvCountExtra::getProgramkey, extra -> extra));
            if (CollUtil.isNotEmpty(mapOldExtras)){
                InvCountExtra syncStatusExtra = mapOldExtras.get(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_STATUS);
                InvCountExtra syncMsgExtra = mapOldExtras.get(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_ERROR_MSG);

                callWmsInterface(warehouse, headerDTO, lineMap, syncStatusExtra, syncMsgExtra);
            }else {
                InvCountExtra syncStatusExtra = new InvCountExtra().
                        setTenantid(BaseConstants.DEFAULT_TENANT_ID)
                        .setSourceid(headerDTO.getCountHeaderId())
                        .setEnabledflag(1)
                        .setProgramkey(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_STATUS);

                InvCountExtra syncMsgExtra  = new InvCountExtra().
                        setTenantid(BaseConstants.DEFAULT_TENANT_ID)
                        .setSourceid(headerDTO.getCountHeaderId())
                        .setEnabledflag(1)
                        .setProgramkey(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_ERROR_MSG);

                callWmsInterface(warehouse, headerDTO, lineMap, syncStatusExtra, syncMsgExtra);
            }
        }

        if (CollUtil.isNotEmpty(errorList)){
            String errorMsg = String.join("|", errorList);
            throw new CommonException(errorMsg);
        }
    }

    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @Override
    public List<InvCountHeaderDTO> selectListForDataSet(InvCountHeaderDTO invoiceApplyHeaderDTO) {
        List<InvCountHeaderDTO> headerDTOS = invCountHeaderRepository.selectList(invoiceApplyHeaderDTO);

        //Collect All HeaderId, to query linelist
        Set<Long> headerIds = new HashSet<>();
        for (InvCountHeaderDTO dto : headerDTOS) {
            headerIds.add(dto.getCountHeaderId());
        }

        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIds);

        List<InvCountLine> invoiceApplyLines = lineRepository.selectByCondition(condition);

        //Create Map for each header lines
        Map<Long, List<InvCountLine>> lineHeaders = invoiceApplyLines.stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            //Create listCounter and listSupervisor
            List<IamUserDTO> counterList = createListUser(headerDTO.getCounterIds());
            List<IamUserDTO> supervisorList = createListUser(headerDTO.getSupervisorIds());

            //MaterialList
            List<InvMaterial> materialList = materialRepository.selectByIds(headerDTO.getSnapshotMaterialIds());
            List<SnapshotMaterialDTO> snapshotMaterialDTOList = new ArrayList<>();
            for (InvMaterial material : materialList) {
                SnapshotMaterialDTO materialDTO = new SnapshotMaterialDTO();
                materialDTO.setId(material.getMaterialId());
                materialDTO.setMaterialCode(material.getMaterialCode());
                snapshotMaterialDTOList.add(materialDTO);
            }

            //Batch List
            List<InvBatch> batchList = batchRepository.selectByIds(headerDTO.getSnapshotBatchIds());
            List<SnapshotBatchDTO> snapshotBatchDTOS = new ArrayList<>();
            for (InvBatch batch : batchList) {
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

            headerDTO.setInvCountLineDTOList(lineHeaders.get(headerDTO.getCountHeaderId()));

        }

        if (invoiceApplyHeaderDTO.getDelFlag() == null) {
            invoiceApplyHeaderDTO.setDelFlag(0);
        }

        return headerDTOS;
    }

    private void callWmsInterface(InvWarehouse warehouse,
                                 InvCountHeaderDTO headerDTO,
                                 Map<Long, List<InvCountLine>> lineMap,
                                 InvCountExtra syncStatusExtra,
                                 InvCountExtra syncMsgExtra
                                 ){
        if (warehouse.getIsWmsWarehouse() == 1){
            headerDTO.setInvCountLineDTOList(lineMap.get(headerDTO.getCountHeaderId()));
            headerDTO.setEmployeeNumber("21995");
            ResponsePayloadDTO response =  invokeWms(headerDTO);
            JSONObject jsonObject = new JSONObject(Objects.requireNonNull(response.getBody()));

            if (Objects.equals(jsonObject.getString("returnStatus"), "S")) {
                syncStatusExtra.setProgramvalue("SUCCESS");
                syncMsgExtra.setProgramvalue("");
                headerDTO.setRelatedWmsOrderCode(jsonObject.getString("code"));
            } else {
                syncStatusExtra.setProgramvalue("ERROR");
                syncMsgExtra.setProgramvalue(jsonObject.getString("returnMsg"));
            }
        }else {
            syncStatusExtra.setProgramvalue("SKIP");
        }
    }

    private ResponsePayloadDTO invokeWms(InvCountHeaderDTO invCountHeaderDTO) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setPayload(JSON.toJSONString(invCountHeaderDTO));
        return  interfaceInvokeSdk.invoke(InvoiceCountHeaderConstant.ExternalInterface.INTERFACE_NAME_SPACE,
                InvoiceCountHeaderConstant.ExternalInterface.INTERFACE_SERVER_CODE,
                InvoiceCountHeaderConstant.ExternalInterface.INTERFACE_CODE,
                requestPayloadDTO);
    }

    private List<String> getValidLovValues(String lovCode) {
        return lovAdapter
                .queryLovValue(lovCode, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private void saveLines(List<InvCountHeaderDTO> listHeaderDTOSaved) {
        for (InvCountHeaderDTO headerDTO : listHeaderDTOSaved) {
            List<InvCountLine> lines = headerDTO.getInvCountLineDTOList();
            if (CollUtil.isEmpty(lines)) {
                continue;
            }
            lineService.saveData(lines);
        }
    }

    private void validateCountValueSet(InvCountHeader countHeader,
                                       List<String> errorList,
                                       List<String> validCountDimensions,
                                       List<String> validCountTypes,
                                       List<String> validCountModes) {
        if (!validCountDimensions.contains(countHeader.getCountDimension())) {
            String errorMsg = "Header Id " + countHeader.getCountHeaderId() + " error: Dimension just allow SKU & LOT";
            errorList.add(errorMsg);
        }
        if (!validCountTypes.contains(countHeader.getCountType())) {
            String errorMsg = "Header Id " + countHeader.getCountHeaderId() + " error: Type just allow MONTH & YEAR";
            errorList.add(errorMsg);
        }
        if (!validCountModes.contains(countHeader.getCountMode())) {
            String errorMsg = "Header Id " + countHeader.getCountHeaderId() + " error: Mode just allow VISIBLE_COUNT & UNVISIBLE_COUNT";
            errorList.add(errorMsg);
        }
    }
}

