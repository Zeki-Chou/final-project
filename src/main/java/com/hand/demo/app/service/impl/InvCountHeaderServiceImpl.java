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
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.core.base.BaseConstants;
import com.hand.demo.app.service.InvCountHeaderService;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.mybatis.domian.Condition;
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Autowired
    private ProfileClient profileClient;
    @Autowired
    private WorkflowClient workflowClient;
    @Autowired
    private IamRemoteService iamRemoteService;
    @Autowired
    private UserRepository userRepository;

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
        for (IamUserDTO iamUserDTO : counterList){
            invCountHeaderDTO.setCounters(iamUserDTO.getRealName());
        }

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
        invCountHeaderDTO.setCountOrderLineList(lineList);
        invCountHeaderDTO.setIsWmsWarehouse(isWMS);

        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
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
            return new InvCountInfoDTO();
        }

        //Get Verification Data for update
        List<InvCountHeader> existingHeader = getExistingHeader(updateList);
        if (CollUtil.isEmpty(existingHeader)) {
            String errorMsg = "error: Request update not Found in database";
            errorList.add(errorMsg);
            return setErrorInfoDTO(errorList);
        }

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
        for (InvCountHeaderDTO headerDTO : updateList) {
            if (!headerDTO.getStatus().isEmpty()) {
                String errorMsg = "Update headerId " + headerDTO.getCountHeaderId() + "error: status field cannot be updated";
                errorList.add(errorMsg);
            }
        }

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
            InvCountInfoDTO infoDTO = setErrorInfoDTO(errorList);
            return infoDTO;
        }
        return new InvCountInfoDTO();
    }

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO infoDTO = manualSaveCheck(invCountHeaders);
        if (infoDTO.getErrorMsg() != null) {
            throw new CommonException(infoDTO.getErrorMsg());
        }

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
        }
        if (CollUtil.isNotEmpty(updateForDraftStatus)) {
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateForDraftStatus));
        }
        if (CollUtil.isNotEmpty(updateRemark)) {
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateRemark), InvCountHeader.FIELD_REMARK);
        }
        if (CollUtil.isNotEmpty(updateReason)) {
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateReason), InvCountHeader.FIELD_REASON);
        }

        return invCountHeaders;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<String> errorList = new ArrayList<>();
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            if (!headerDTO.getCountStatus().equals("DRAFT")) {
                String errorMsg = "headerId " + headerDTO.getCountHeaderId() + " error, only Draft status can deleted";
                errorList.add(errorMsg);
            }
            if (!headerDTO.getCreatedBy().equals(currentUserId)) {
                String errorMsg = "headerId " + headerDTO.getCountHeaderId() + " error, this createdBy not matches with currentUser";
                errorList.add(errorMsg);
            }
        }

        if (CollUtil.isNotEmpty(errorList)) {
            InvCountInfoDTO infoDTO = setErrorInfoDTO(errorList);
            return infoDTO;
        }

        invCountHeaderRepository.batchDeleteByPrimaryKey(new ArrayList<>(invCountHeaderDTOS));

        String headerSuccessIds = invCountHeaderDTOS.stream()
                .map(header -> String.valueOf(header.getCountHeaderId()))
                .collect(Collectors.joining(","));
        String succesMsg = "Succes Deleted ids : " + headerSuccessIds;
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();
        infoDTO.setSuccessMsg(succesMsg);
        return infoDTO;
    }

    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Requery based on headerId
        List<InvCountHeader> existingCountHeaders = getExistingHeader(invCountHeaderDTOS);

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
            InvCountInfoDTO infoDTO = setErrorInfoDTO(errorList);
            return infoDTO;
        }

        String headerSuccessIds = existingCountHeaders.stream()
                .map(header -> String.valueOf(header.getCountHeaderId()))
                .collect(Collectors.joining(","));
        String succesMsg = "Success Verification ids : " + headerSuccessIds;
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();
        infoDTO.setSuccessMsg(succesMsg);
        return infoDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Call verification
        InvCountInfoDTO infoDTO = executeCheck(invCountHeaderDTOS);
        if (infoDTO.getErrorMsg() != null) {
            throw new CommonException(infoDTO.getErrorMsg());
        }

        //Query All stock and make key concat of all the param to optimize query every loop
        List<InvStock> stocks = stockRepository.selectList(new InvStock());
        Map<String, List<InvStock>> stockMap = new HashMap<>();

        for (InvStock stock : stocks) {
            Long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
            String key = (stock.getTenantId() != 0L)
                    ? stock.getTenantId().toString() + stock.getCompanyId().toString() + departmentId.toString() + stock.getWarehouseId().toString()
                    : stock.getCompanyId().toString() + departmentId.toString() + stock.getWarehouseId().toString();

            stockMap.computeIfAbsent(key, k -> new ArrayList<>()).add(stock);
        }

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            headerDTO.setCountStatus("INCOUNTING");
            if (headerDTO.getCountType().equals("MONTH")) {
                String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-mm"));
                headerDTO.setCountTimeStr(yearMonth);
            }
            if (headerDTO.getCountType().equals("YEAR")) {
                String year = Year.now().format(DateTimeFormatter.ofPattern("yyyy"));
                headerDTO.setCountTimeStr(year);
            }

            //Query the Stock from param in header
            String key = (headerDTO.getTenantId() != 0L)
                    ? headerDTO.getTenantId().toString() + headerDTO.getCompanyId().toString() + headerDTO.getDepartmentId().toString() + headerDTO.getWarehouseId().toString()
                    : headerDTO.getCompanyId().toString() + headerDTO.getDepartmentId().toString() + headerDTO.getWarehouseId().toString();
            List<InvStock> relatedStocks = stockMap.get(key);

            List<Long> listMaterialId = convertIdsToListLong(headerDTO.getSnapshotMaterialIds());
            List<Long> listBatchId = convertIdsToListLong(headerDTO.getSnapshotBatchIds());

            //Conditioning by dimension
            List<InvCountLine> lineList = new ArrayList<>();
            if (headerDTO.getCountDimension().equals("SKU")) {
                //SKU = Summarize by materialId and save in line
                for (Long materialId : listMaterialId) {
                    BigDecimal snapUnitQty = BigDecimal.ZERO;
                    String unitCode = "";
                    Long batchId = null;
                    for (InvStock stock : relatedStocks) {
                        if (materialId.equals(stock.getMaterialId()) && listBatchId.contains(stock.getBatchId())) {
                            snapUnitQty = snapUnitQty.add(stock.getUnitQuantity());
                            unitCode = stock.getUnitCode();
                            batchId = stock.getBatchId();
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
                    line.setBatchId(batchId);

                    lineList.add(line);
                }
            }
            if (headerDTO.getCountDimension().equals("LOT")) {
                //LOT = Summarize by materialId and batchId, then save in line
                for (Long materialId : listMaterialId) {
                    for (Long batchId : listBatchId) {
                        BigDecimal snapUnitQty = BigDecimal.ZERO;
                        String unitCode = "";
                        for (InvStock stock : relatedStocks) {
                            if (materialId.equals(stock.getMaterialId()) && batchId.equals(stock.getBatchId())) {
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
                        line.setBatchId(batchId);

                        lineList.add(line);
                    }
                }
            }
            headerDTO.setCountOrderLineList(lineList);
        }
        invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(invCountHeaderDTOS),
                InvCountHeader.FIELD_COUNT_STATUS);
        saveLines(invCountHeaderDTOS);
        return invCountHeaderDTOS;
    }

    @Override
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Query Existing Data to optimize query looping
        List<Long> listWarehouseId = invCountHeaderDTOS.stream()
                .map(InvCountHeaderDTO::getWarehouseId) // Extract warehouseId
                .collect(Collectors.toList());
        String existWarehousIds = convertLongToIdsString(listWarehouseId);
        List<InvWarehouse> warehouseList = invWarehouseRepository.selectByIds(existWarehousIds);
        Map<Long, InvWarehouse> warehouseMap = warehouseList.stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, warehouse -> warehouse));

        List<Long> headerIdList = invCountHeaderDTOS.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .collect(Collectors.toList());
        Condition conditionLine = new Condition(InvCountLine.class);
        Condition.Criteria criteriaLine = conditionLine.createCriteria();
        criteriaLine.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIdList);
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

        List<InvCountHeaderDTO> updateList = new ArrayList<>();

        List<InvCountExtra> insertExtraList = new ArrayList<>();
        List<InvCountExtra> updateExtraList = new ArrayList<>();

        //Query warehouse data based on warehouseId
        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            InvWarehouse warehouse = warehouseMap.get(headerDTO.getWarehouseId());
            if (warehouse == null) {
                String errorMsg = "Header Id " + headerDTO.getCountHeaderId() + " error: Warehouse is not Found";
                errorList.add(errorMsg);
            }
            List<InvCountExtra> extras = extraMap.get(headerDTO.getCountHeaderId());
            Map<String, InvCountExtra> mapOldExtras = null;
            if (CollUtil.isNotEmpty(extras) || extras != null) {
                mapOldExtras = extras
                        .stream()
                        .collect(Collectors.toMap(InvCountExtra::getProgramkey, extra -> extra));
            }

            if (CollUtil.isNotEmpty(mapOldExtras) || mapOldExtras != null) {
                InvCountExtra syncStatusExtra = mapOldExtras.get(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_STATUS);
                InvCountExtra syncMsgExtra = mapOldExtras.get(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_ERROR_MSG);

                callWmsInterface(warehouse, headerDTO, lineMap, syncStatusExtra, syncMsgExtra, updateList);
                updateExtraList.add(syncStatusExtra);
                updateExtraList.add(syncMsgExtra);
            } else {
                InvCountExtra syncStatusExtra = new InvCountExtra().
                        setTenantid(BaseConstants.DEFAULT_TENANT_ID)
                        .setSourceid(headerDTO.getCountHeaderId())
                        .setEnabledflag(1)
                        .setProgramkey(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_STATUS);

                InvCountExtra syncMsgExtra = new InvCountExtra().
                        setTenantid(BaseConstants.DEFAULT_TENANT_ID)
                        .setSourceid(headerDTO.getCountHeaderId())
                        .setEnabledflag(1)
                        .setProgramkey(InvoiceCountHeaderConstant.Extra.PROGRAM_KEY_ERROR_MSG);

                callWmsInterface(warehouse, headerDTO, lineMap, syncStatusExtra, syncMsgExtra, updateList);
                insertExtraList.add(syncStatusExtra);
                insertExtraList.add(syncMsgExtra);
            }
        }
        extraRepository.batchInsertSelective(insertExtraList);
        extraRepository.batchUpdateByPrimaryKeySelective(updateExtraList);

        if (CollUtil.isNotEmpty(errorList)) {
            InvCountInfoDTO infoDTO = setErrorInfoDTO(errorList);
            return infoDTO;
        }
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateList));
        return new InvCountInfoDTO();
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO) {
        String wmsError = "The current warehouse is not a WMS warehouse, operations are not allowed";
        String lineConsistError = "The counting order line data is inconsistent with the INV system, please check the data";
        String status = "E";
        String currentUser = DetailsHelper.getUserDetails().getUserId().toString();

        Integer isWmsWareHouse = warehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId()).getIsWmsWarehouse();
        if (isWmsWareHouse != 1) {
            invCountHeaderDTO.setErrorMsg(wmsError);
            invCountHeaderDTO.setStatus(status);
            return invCountHeaderDTO;
        }

        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo(InvCountLine.FIELD_COUNT_HEADER_ID, invCountHeaderDTO.getCountHeaderId());

        List<InvCountLine> listLine = lineRepository.selectByCondition(condition);
        List<Long> listLineId = listLine.stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toList());

        List<InvCountLine> listLineRequest = invCountHeaderDTO.getCountOrderLineList();
        List<Long> listLineIdRequest = listLineRequest.stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toList());
        Map<Long, InvCountLine> lineRequestMap = listLineRequest.stream()
                .collect(Collectors.toMap(InvCountLine::getCountLineId, invCountLine -> invCountLine));

        List<InvCountLine> updateList = new ArrayList<>();

        for (Long lineIdRequest : listLineIdRequest) {
            if (listLineId.size() != listLineIdRequest.size() || !listLineId.contains(lineIdRequest)) {
                invCountHeaderDTO.setErrorMsg(lineConsistError);
                invCountHeaderDTO.setStatus(status);
                return invCountHeaderDTO;
            }
        }

        for (InvCountLine lineUpdate : listLine) {
            InvCountLine requestLine = lineRequestMap.get(lineUpdate.getCountLineId());
            lineUpdate.setUnitQty(requestLine.getUnitQty());
            lineUpdate.setUnitDiffQty(lineUpdate.getUnitQty().subtract(lineUpdate.getSnapshotUnitQty()));
            lineUpdate.setRemark(requestLine.getRemark());
            lineUpdate.setCounterIds(currentUser);
            updateList.add(lineUpdate);
        }

        lineRepository.batchUpdateOptional(updateList,
                InvCountLine.FIELD_UNIT_QTY,
                InvCountLine.FIELD_UNIT_DIFF_QTY,
                InvCountLine.FIELD_REMARK);
        invCountHeaderDTO.setCountOrderLineList(updateList);

        invCountHeaderDTO.setStatus("S");

        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        //Requery database based on input headerId
        List<InvCountHeader> existingHeaders = getExistingHeader(invCountHeaderDTOList);

        //Verification
        List<String> errorList = new ArrayList<>();
        List<String> allowedCountStatus = Arrays.asList("INCOUNTING", "PROCESSING", "REJECTED", "WITHDRAWN");

        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        Set<Long> headerIds = new HashSet<>();
        for (InvCountHeaderDTO dto : invCountHeaderDTOList) {
            headerIds.add(dto.getCountHeaderId());
        }

        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIds);

        List<InvCountLine> invoiceApplyLines = lineRepository.selectByCondition(condition);

        //Create Map for each header lines
        Map<Long, List<InvCountLine>> lineHeaders = invoiceApplyLines.stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));


        Set<Long> headerIdWithDifferent = new HashSet<>();

        for (InvCountHeader header : existingHeaders) {
            //Check Document Status
            if (!allowedCountStatus.contains(header.getCountStatus())) {
                String errorMsg = "HeaderId " + header.getCountHeaderId() + "error: only in counting, processing, rejected, and withdrawn status can submit";
                errorList.add(errorMsg);
            }

            //current login user validation
            List<Long> listSupervisorId = convertIdsToListLong(header.getSupervisorIds());
            if (!listSupervisorId.contains(currentUserId)) {
                String errorMsg = "HeaderId " + header.getCountHeaderId() + "error: Only the current login user is the supervisor can submit document";
                errorList.add(errorMsg);
            }

            //Data Integrity Check
            List<InvCountLine> lineList = lineHeaders.get(header.getCountHeaderId());
            for (InvCountLine line : lineList) {
                if (line.getUnitQty() == null) {
                    String errorMsg = "HeaderId " + header.getCountHeaderId() + ", LineId " + line.getCountLineId() + "error: There are data rows with empty count quantity. Please check the data";
                    errorList.add(errorMsg);
                }
                if (line.getUnitDiffQty() != null) {
                    headerIdWithDifferent.add(header.getCountHeaderId());
                }
            }
        }

        if (CollUtil.isNotEmpty(headerIdWithDifferent)) {
            for (InvCountHeaderDTO headerDTO : invCountHeaderDTOList) {
                if (headerIdWithDifferent.contains(headerDTO.getCountHeaderId())) {
                    if (headerDTO.getReason() == null) {
                        String errorMsg = "Request HeaderId " + headerDTO.getCountHeaderId() + "error: There some difference counting, the reason field must be entered.";
                        errorList.add(errorMsg);
                    }
                }
            }
        }

        if (CollUtil.isNotEmpty(errorList)) {
            InvCountInfoDTO infoDTO = setErrorInfoDTO(errorList);
            return infoDTO;
        }

        return new InvCountInfoDTO();
    }

    @Override
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO infoDTO = submitCheck(invCountHeaderDTOS);
        if (!infoDTO.getErrorMsg().isEmpty() || infoDTO.getErrorMsg() != null) {
            throw new CommonException(infoDTO.getErrorMsg());
        }

        String workflowFlag = profileClient.getProfileValueByOptions(
                BaseConstants.DEFAULT_TENANT_ID,
                null,
                null,
                "FEXAM61.INV.COUNTING.ISWORKFLO");
        String departmentIds = invCountHeaderDTOS
                .stream()
                .map(InvCountHeaderDTO::getDepartmentId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Map<Long, String> listDepartment = departmentRepository.selectByIds(departmentIds)
                .stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, IamDepartment::getDepartmentCode));

        if (workflowFlag.equals("1")) {
            for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
                Map<String, Object> mapsDepartment = new HashMap<>();
                mapsDepartment.put("departmentCode", listDepartment.get(invCountHeaderDTO.getDepartmentId()));
                workflowClient.startInstanceByFlowKey(
                        DetailsHelper.getUserDetails().getTenantId(),
                        InvoiceCountHeaderConstant.Workflow.FLOW_KEY,
                        invCountHeaderDTO.getCountNumber(),
                        "EMPLOYEE",
                        "47361",
                        mapsDepartment);
            }
        } else {
            List<InvCountHeader> invCountHeaders = invCountHeaderDTOS
                    .stream().filter(data -> data.getCountHeaderId() != null)
                    .collect(Collectors.toList());
            invCountHeaders.forEach(invCountHeader -> {
                invCountHeader.setCountStatus("CONFIRMED");
            });

            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders);
        }

        return invCountHeaderDTOS;
    }

    @Override
    public InvCountHeaderDTO approvalCallback(Long organizationId, WorkFlowEventDTO workFlowEventDTO) {
        InvCountHeader exampInvCountHeader = new InvCountHeader();
        exampInvCountHeader.setCountNumber(workFlowEventDTO.getBusinessKey());

        InvCountHeader invCountHeader = invCountHeaderRepository.selectOne(exampInvCountHeader);
        invCountHeader.setWorkflowId(workFlowEventDTO.getWorkflowId());
        invCountHeader.setCountStatus(workFlowEventDTO.getDocStatus());
        invCountHeader.setApprovedTime(workFlowEventDTO.getApprovedTime());

        invCountHeaderRepository.updateByPrimaryKey(invCountHeader);

        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);

        return invCountHeaderDTO;

    }

    @Override
    @ProcessCacheValue
    public List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTO) {
        List<InvCountHeaderDTO> headerDTOS = invCountHeaderRepository.selectList(invCountHeaderDTO);

        //Collect All HeaderId, to query linelist
        Set<Long> headerIdSet = headerDTOS.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .collect(Collectors.toSet());

        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIdSet);

        List<InvCountLine> invoiceApplyLines = lineRepository.selectByCondition(condition);

        //Create Map for each header lines
        Map<Long, List<InvCountLine>> mapLineHeaders = invoiceApplyLines.stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));

        String departmentIds = headerDTOS.stream()
                .map(InvCountHeaderDTO::getDepartmentId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Map<Long, String> mapDepartmentName = departmentRepository.selectByIds(departmentIds).stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, IamDepartment::getDepartmentName));

        String warehouseIds = headerDTOS.stream()
                .map(InvCountHeaderDTO::getWarehouseId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Map<Long, String> mapWarehouseCode = warehouseRepository.selectByIds(warehouseIds).stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, InvWarehouse::getWarehouseCode));

        Set<String> materialIdSet = headerDTOS.stream()
                .flatMap(header -> Arrays.stream(header.getSnapshotMaterialIds().split(",")))
                .map(String::trim)
                .collect(Collectors.toSet());
        String materialIds = String.join(",", materialIdSet);
        Map<Long, InvMaterial> mapMaterial = materialRepository.selectByIds(materialIds).stream()
                .collect(Collectors.toMap(InvMaterial::getMaterialId, Function.identity()));

        Set<String> batchIdSet = headerDTOS.stream()
                .flatMap(header -> Arrays.stream(header.getSnapshotBatchIds().split(",")))
                .map(String::trim)
                .collect(Collectors.toSet());
        String batchIds = String.join(",", batchIdSet);
        Map<Long, InvBatch> mapBatch = batchRepository.selectByIds(batchIds).stream()
                .collect(Collectors.toMap(InvBatch::getBatchId, Function.identity()));

        Set<Long> existingUserIdSet = headerDTOS.stream()
                .flatMap(headerDTO -> Stream.of(headerDTO.getCounterIds(), headerDTO.getSupervisorIds()))
                .filter(Objects::nonNull)
                .flatMap(ids -> Arrays.stream(ids.split(",")))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        String existUserIds = convertLongToIdsString(existingUserIdSet);

        Map<Long, String> mapRealName = userRepository.selectByIds(existUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            String iamUserString = iamRemoteService.selectSelf().getBody();
            JSONObject jsonIam = new JSONObject(iamUserString);
            String tenantCode = jsonIam.getString("tenantName");
            String departmentName = mapDepartmentName.get(headerDTO.getDepartmentId());
            String warehouseCode = mapWarehouseCode.get(headerDTO.getWarehouseId());
            String creator = DetailsHelper.getUserDetails().getRealName();

            String counters = "";
            if (headerDTO.getCounterIds() != null){
                List<Long> listCounterId = convertIdsToListLong(headerDTO.getCounterIds());
                List<String> listRealName = new ArrayList<>();
                for (Long counterId : listCounterId){
                    String realName = mapRealName.get(counterId);
                    listRealName.add(realName);
                }
                counters = String.join(", ", listRealName);
            }

            String supervisors = "";
            if (headerDTO.getSupervisorIds() != null){
                List<Long> listSupervisorId = convertIdsToListLong(headerDTO.getSupervisorIds());
                List<String> listRealName = new ArrayList<>();
                for (Long supervisorId : listSupervisorId){
                    String realName = mapRealName.get(supervisorId);
                    listRealName.add(realName);
                }
                supervisors = String.join(", ", listRealName);
            }

            String materials = "";
            if (headerDTO.getSnapshotMaterialIds() != null){
                List<Long> listMaterialId = convertIdsToListLong(headerDTO.getSnapshotMaterialIds());
                List<String> listMaterialName = new ArrayList<>();
                for (Long materialId : listMaterialId){
                    String materialName = mapMaterial.get(materialId).getMaterialName();
                    listMaterialName.add(materialName);
                }
                materials = String.join(", ", listMaterialName);
            }

            //Batch
            String batches = "";
            if (headerDTO.getSnapshotBatchIds() != null){
                List<Long> listBatchId = convertIdsToListLong(headerDTO.getSnapshotBatchIds());
                List<String> listBatchCode = new ArrayList<>();
                for (Long batchId : listBatchId){
                    String batchCode = mapBatch.get(batchId).getBatchCode();
                    listBatchCode.add(batchCode);
                }
                batches = String.join(", ", listBatchCode);
            }

            //Lines
            List<InvCountLine> lineList = mapLineHeaders.get(headerDTO.getCountHeaderId());
            List<InvCountLineDTO> lineDTOList = new ArrayList<>();
            for (InvCountLine line : lineList){
                InvCountLineDTO lineDTO = new InvCountLineDTO();
                BeanUtils.copyProperties(line, lineDTO);
                lineDTO.setMaterialCode(mapMaterial.get(lineDTO.getMaterialId()).getMaterialCode());
                lineDTO.setMaterialName(mapMaterial.get(lineDTO.getMaterialId()).getMaterialName());
                lineDTO.setBatchCode(mapBatch.get(lineDTO.getBatchId()).getBatchCode());
                List<Long> counterIdList = convertIdsToListLong(lineDTO.getCounterIds());
                lineDTO.setCounter(mapRealName.get(counterIdList.get(0)));
                lineDTOList.add(lineDTO);
            }

            //Set to DTO
            headerDTO.setTenantCode(tenantCode);
            headerDTO.setDepartmentName(departmentName);
            headerDTO.setWarehouseCode(warehouseCode);
            headerDTO.setCreator(creator);
            headerDTO.setCounters(counters);
            headerDTO.setSupervisors(supervisors);
            headerDTO.setMaterials(materials);
            headerDTO.setBatches(batches);
            headerDTO.setCountOrderLineListDTO(lineDTOList);
            if (headerDTO.getWorkflowId() != null){
                headerDTO.setApprovalHistory(workflowClient.approveHistory(headerDTO.getTenantId(), headerDTO.getWorkflowId()));
            }
        }
        return headerDTOS;
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

    private void callWmsInterface(InvWarehouse warehouse,
                                  InvCountHeaderDTO headerDTO,
                                  Map<Long, List<InvCountLine>> lineMap,
                                  InvCountExtra syncStatusExtra,
                                  InvCountExtra syncMsgExtra,
                                  List<InvCountHeaderDTO> updateList
    ) {
        if (warehouse != null && warehouse.getIsWmsWarehouse() == 1) {
            headerDTO.setCountOrderLineList(lineMap.get(headerDTO.getCountHeaderId()));
            headerDTO.setEmployeeNumber(DetailsHelper.getUserDetails().getUsername());
            Map<String, String> requestHeaderMap = new HashMap<>();
            requestHeaderMap.put("Authorization", "ece48577-f272-4baf-8422-eb0f28ade252");
            ResponsePayloadDTO response = invokeWms(headerDTO, requestHeaderMap);
            JSONObject jsonObject = new JSONObject(response.getPayload());

            if (Objects.equals(jsonObject.getString("returnStatus"), "S")) {
                syncStatusExtra.setProgramvalue("SUCCESS");
                syncMsgExtra.setProgramvalue("");
                headerDTO.setRelatedWmsOrderCode(jsonObject.getString("code"));
                updateList.add(headerDTO);
            } else {
                syncStatusExtra.setProgramvalue("ERROR");
                syncMsgExtra.setProgramvalue(jsonObject.getString("returnMsg"));
            }
        } else {
            syncStatusExtra.setProgramvalue("SKIP");
            syncMsgExtra.setProgramvalue("SKIP");
        }
    }

    private List<InvCountHeader> getExistingHeader(List<InvCountHeaderDTO> headerDTOList) {
        //Get Verification Data for update
        Set<Long> countHeaderIdList = new HashSet<>();
        for (InvCountHeaderDTO dto : headerDTOList) {
            countHeaderIdList.add(dto.getCountHeaderId());
        }

        String countHeaderIdsString = convertLongToIdsString(countHeaderIdList);

        return invCountHeaderRepository.selectByIds(countHeaderIdsString);
    }

    private ResponsePayloadDTO invokeWms(InvCountHeaderDTO headerDTO, Map<String, String> requestHeaderMap) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setHeaderParamMap(requestHeaderMap);
        requestPayloadDTO.setMediaType("application/json");
        requestPayloadDTO.setPayload(JSON.toJSONString(headerDTO));
        return interfaceInvokeSdk.invoke(InvoiceCountHeaderConstant.ExternalInterface.INTERFACE_NAME_SPACE,
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
        List<InvCountLine> insertLines = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : listHeaderDTOSaved) {
            List<InvCountLine> lines = headerDTO.getCountOrderLineList();
            if (CollUtil.isEmpty(lines)) {
                continue;
            }
            insertLines.addAll(lines);
        }
        lineService.saveData(insertLines);
    }

    private List<Long> convertIdsToListLong(String ids) {
        return Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    private String convertLongToIdsString(List<Long> listId) {
        return listId.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private String convertLongToIdsString(Set<Long> listId) {
        return listId.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
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

    private InvCountInfoDTO setErrorInfoDTO(List<String> errorList) {
        String errorMsg = String.join("|", errorList);

        InvCountInfoDTO infoDTO = new InvCountInfoDTO();
        infoDTO.setErrorMsg(errorMsg);

        return infoDTO;
    }
}