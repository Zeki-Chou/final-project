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
import io.seata.common.util.StringUtils;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.base.BaseConstants;
import com.hand.demo.app.service.InvCountHeaderService;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.TokenUtils;
import org.hzero.mybatis.domian.Condition;
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
        String iamUserString = iamRemoteService.selectSelf().getBody();
        JSONObject jsonIam = new JSONObject(iamUserString);
        Boolean tenantAdminFlag = jsonIam.optBoolean("tenantAdminFlag", false);
        invCountHeader.setTenantAdminFlag(tenantAdminFlag);
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
        List<InvCountLineDTO> lineDTOS = new ArrayList<>();
        BeanUtils.copyProperties(lineList, lineDTOS);
        for (InvCountLine line : lineList) {
            InvCountLineDTO lineDTO = new InvCountLineDTO();
            BeanUtils.copyProperties(line, lineDTO);
            lineDTO.setSupervisorIds(invCountHeaderDTO.getSupervisorIds());
            lineDTOS.add(lineDTO);
        }
        //Query is WMS
        Integer isWMS = warehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId()).getIsWmsWarehouse();
        //Set to DTO
        invCountHeaderDTO.setCounterList(counterList);
        invCountHeaderDTO.setSupervisorList(supervisorList);
        invCountHeaderDTO.setSnapshotMaterialList(snapshotMaterialDTOList);
        invCountHeaderDTO.setSnapshotBatchList(snapshotBatchDTOS);
        invCountHeaderDTO.setCountOrderLineListDTO(lineDTOS);
        invCountHeaderDTO.setIsWmsWarehouse(isWMS);

        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
        //For collect Error
        List<String> errorList = new ArrayList<>();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
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
            invCountInfoDTO.setErrorList(updateList);
            String errorMsg = "error: All request update not Found in database";
            invCountInfoDTO.setErrorMsg(errorMsg);
            return invCountInfoDTO;
        }

        Map<Long, InvCountHeader> mapHeader = existingHeader.stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        //Get Existing WareHouse from Existing Header
        Set<Long> wareHouseIds = new HashSet<>();
        for (InvCountHeader header : existingHeader) {
            wareHouseIds.add(header.getWarehouseId());
        }
        String wareHouseIdsString = wareHouseIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Map<Long, InvWarehouse> warehouseMap = invWarehouseRepository.selectByIds(wareHouseIdsString).stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, Function.identity()));

        //Validation for Update
        List<InvCountHeaderDTO> errorListHeader = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : updateList) {
            if (!headerDTO.getStatus().isEmpty()) {
                String errorMsg = "Update headerId " + headerDTO.getCountHeaderId() + "error: status field cannot be updated manually";
                errorList.add(errorMsg);
            }
            InvCountHeader header = mapHeader.get(headerDTO.getCountHeaderId());
            if (header == null) {
                headerDTO.setErrorMsg("headerId " + headerDTO.getCountHeaderId() + " not Found in database");
                errorListHeader.add(headerDTO);
                continue;
            }
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
                if (BaseConstants.Flag.YES.equals(warehouse.getIsWmsWarehouse()) && !supervisorIds.contains(currentUserId)) {
                    String errorMsg = "Update headerId " + header.getCountHeaderId() + "error: The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate";
                    errorList.add(errorMsg);
                }
                if (!counterIds.contains(currentUserId) && !supervisorIds.contains(currentUserId) && !header.getCreatedBy().equals(currentUserId)) {
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
        //CurrentUser
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

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
            dto.setDelFlag(BaseConstants.Flag.NO);
        }

        //Update according Status
        List<InvCountHeaderDTO> updateForDraftStatus = new ArrayList<>();
        List<InvCountHeaderDTO> updateForCountingStatus = new ArrayList<>();
        List<InvCountHeaderDTO> updateForRejected = new ArrayList<>();
        for (InvCountHeaderDTO dto : updateList) {
            if (dto.getCountStatus().equals("DRAFT")) {
                if (!dto.getReason().isEmpty()) {
                    dto.setReason(null);
                }
                updateForDraftStatus.add(dto);
            }
            if (dto.getCountStatus().equals("INCOUNTING")) {
                if (CollUtil.isNotEmpty(dto.getCountOrderLineList())) {
                    updateLines(dto.getCountOrderLineList(), currentUserId);
                }
                updateForCountingStatus.add(dto);
            }
            if (dto.getCountStatus().equals("REJECTED")) {
                if (!dto.getReason().isEmpty()) {
                    updateForRejected.add(dto);
                }
            }
        }

        if (CollUtil.isNotEmpty(insertList)) {
            invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
        }
        if (CollUtil.isNotEmpty(updateForDraftStatus)) {
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateForDraftStatus));
        }
        if (CollUtil.isNotEmpty(updateForCountingStatus)) {
            for (InvCountHeader countingDTO : updateForCountingStatus) {
                if (!countingDTO.getRemark().isEmpty()) {
                    invCountHeaderRepository.updateOptional(countingDTO, InvCountHeader.FIELD_REMARK);
                }
                if (!countingDTO.getReason().isEmpty()) {
                    invCountHeaderRepository.updateOptional(countingDTO, InvCountHeader.FIELD_REASON);
                }

            }
        }
        if (CollUtil.isNotEmpty(updateForRejected)) {
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(updateForRejected),
                    InvCountHeader.FIELD_REASON);
        }
        return invCountHeaders;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<String> errorList = new ArrayList<>();
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        List<InvCountHeader> existingHeader = getExistingHeader(invCountHeaderDTOS);
        Map<Long, InvCountHeader> mapHeader = existingHeader.stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            InvCountHeader header = mapHeader.get(headerDTO.getCountHeaderId());
            if (header == null) {
                String errorMsg = "headerId " + headerDTO.getCountHeaderId() + " error, this header not found in database";
                errorList.add(errorMsg);
                continue;
            }
            if (!header.getCountStatus().equals("DRAFT")) {
                String errorMsg = "headerId " + header.getCountHeaderId() + " error, only Draft status can deleted";
                errorList.add(errorMsg);
            }
            if (!header.getCreatedBy().equals(currentUserId)) {
                String errorMsg = "headerId " + header.getCountHeaderId() + " error, this createdBy not matches with currentUser";
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
        String successMsg = "Success Deleted ids : " + headerSuccessIds;
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();
        infoDTO.setSuccessMsg(successMsg);
        return infoDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<InvCountHeaderDTO> orderExecution(List<InvCountHeaderDTO> invCountHeaderDTOS){
        InvCountInfoDTO infoSave = manualSaveCheck(invCountHeaderDTOS);
        if (StringUtils.isNotBlank(infoSave.getErrorMsg())){
            throw new CommonException(infoSave.getErrorMsg());
        }

        List<InvCountHeaderDTO> savedHeader = manualSave(invCountHeaderDTOS);

        InvCountInfoDTO executeInfo = executeCheck(savedHeader);
        if (StringUtils.isNotBlank(executeInfo.getErrorMsg())){
            throw new CommonException(executeInfo.getErrorMsg());
        }

        List<InvCountHeaderDTO> executeSave = execute(savedHeader);

        InvCountInfoDTO syncInfo = countSyncWms(executeSave);
        if (StringUtils.isNotBlank(syncInfo.getErrorMsg())){
            throw new CommonException(syncInfo.getErrorMsg());
        }

        return executeSave;
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

        Set<Long> requestCompanyIdset = invCountHeaderDTOS
                .stream().map(InvCountHeaderDTO::getCompanyId)
                .collect(Collectors.toSet());
        String requestCompanyIds = convertLongToIdsString(requestCompanyIdset);
        Set<Long> existingCompanyIdset = companyRepository.selectByIds(requestCompanyIds)
                .stream().map(IamCompany::getCompanyId)
                .collect(Collectors.toSet());

        Set<Long> requestDepartmentIdset = invCountHeaderDTOS
                .stream().map(InvCountHeaderDTO::getDepartmentId)
                .collect(Collectors.toSet());
        String requestDepartmentIds = convertLongToIdsString(requestDepartmentIdset);
        Set<Long> existingDepartmentIdset = departmentRepository.selectByIds(requestDepartmentIds)
                .stream().map(IamDepartment::getDepartmentId)
                .collect(Collectors.toSet());

        Set<Long> requestWarehouseIdset = invCountHeaderDTOS
                .stream().map(InvCountHeaderDTO::getDepartmentId)
                .collect(Collectors.toSet());
        String requestWarehouseIds = convertLongToIdsString(requestWarehouseIdset);
        Set<Long> existingWarehouseIdset = warehouseRepository.selectByIds(requestWarehouseIds)
                .stream().map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toSet());

        //Query All stock and make key = concat of all the param to optimize query every loop
        List<InvStock> stocks = stockRepository.selectList(new InvStock());
        Map<String, InvStock> stockMap = new HashMap<>();
        for (InvStock stock : stocks) {
            Long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
            String key = buildStockKey(stock.getTenantId(), stock.getCompanyId(), departmentId, stock.getWarehouseId(), stock.getMaterialId().toString());
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
            if (!existingCompanyIdset.contains(header.getCompanyId()) || !existingDepartmentIdset.contains(header.getDepartmentId()) || !existingWarehouseIdset.contains(header.getWarehouseId())) {
                String errorMsg = "Header Id " + header.getCountHeaderId() + " error: Only existing company, department, and warehouse can be executed";
                errorList.add(errorMsg);
            }

            //Check Stock by company, department, warehouse and material
            List<String> materialIds = Arrays.stream(header.getSnapshotMaterialIds().split(","))
                    .collect(Collectors.toList());
            for (String materialId : materialIds) {
                String key = buildStockKey(header.getTenantId(), header.getCompanyId(), header.getDepartmentId(), header.getWarehouseId(), materialId);
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
        String successMsg = "Success Verification ids : " + headerSuccessIds;
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();
        infoDTO.setSuccessMsg(successMsg);
        return infoDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Query stock exists by param and make key concat of all the param
        Map<String, List<InvStock>> stockMap = queryStockMap(invCountHeaderDTOS);

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            headerDTO.setCountStatus("INCOUNTING");
            if (headerDTO.getCountType().equals("MONTH")) {
                String yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
                headerDTO.setCountTimeStr(yearMonth);
            }
            if (headerDTO.getCountType().equals("YEAR")) {
                String year = String.valueOf(Year.now().getValue());
                headerDTO.setCountTimeStr(year);
            }

            //Query the Stock from param in header
            String key = buildStockKey(headerDTO.getTenantId(), headerDTO.getCompanyId(), headerDTO.getDepartmentId(), headerDTO.getWarehouseId());
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
    @Transactional(rollbackFor = Exception.class)
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        //Query Existing Data to optimize query looping
        List<Long> listWarehouseId = invCountHeaderDTOS.stream()
                .map(InvCountHeaderDTO::getWarehouseId) // Extract warehouseId
                .collect(Collectors.toList());
        String existWarehouseIds = convertLongToIdsString(listWarehouseId);
        List<InvWarehouse> warehouseList = invWarehouseRepository.selectByIds(existWarehouseIds);
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
        criteriaExtra
                .andEqualTo(InvCountExtra.FIELD_ENABLEDFLAG, 1)
                .andIn(InvCountExtra.FIELD_SOURCEID, headerIdList);
        List<InvCountExtra> extraList = extraRepository.selectByCondition(conditionExtra);
        Map<Long, List<InvCountExtra>> extraMap = null;
        if (CollUtil.isNotEmpty(extraList)){
            extraMap = extraList.stream()
                    .collect(Collectors.groupingBy(InvCountExtra::getSourceid));
        }

        List<String> errorList = new ArrayList<>();

        List<InvCountHeaderDTO> updateList = new ArrayList<>();

        List<InvCountExtra> insertExtraList = new ArrayList<>();
        List<InvCountExtra> updateExtraList = new ArrayList<>();

        for (InvCountHeaderDTO headerDTO : invCountHeaderDTOS) {
            //Query existing warehouse data based on warehouseId
            InvWarehouse warehouse = warehouseMap.get(headerDTO.getWarehouseId());
            if (warehouse == null) {
                String errorMsg = "Header Id " + headerDTO.getCountHeaderId() + " error: Warehouse is not Found";
                errorList.add(errorMsg);
            }
            //Get the extended table data based on the counting header ID
            //extraRepository.select(sourceId=countHeaderId and enabledFlag=1);
            //// if can not get the result, need to initialize
            List<InvCountExtra> extras = extraMap.get(headerDTO.getCountHeaderId());
            Map<String, InvCountExtra> mapOldExtras = null;
            if (CollUtil.isNotEmpty(extras)) {
                mapOldExtras = extras
                        .stream()
                        .collect(Collectors.toMap(InvCountExtra::getProgramkey, Function.identity()));
            }

            if (CollUtil.isNotEmpty(mapOldExtras)) {
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

        insertUpdateExtra(insertExtraList, updateExtraList);

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
        String errorStatus = "E";
        String successStatus = "S";

        Integer isWmsWareHouse = warehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId()).getIsWmsWarehouse();
        if (isWmsWareHouse != 1) {
            invCountHeaderDTO.setErrorMsg(wmsError);
            invCountHeaderDTO.setStatus(errorStatus);
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
                invCountHeaderDTO.setStatus(errorStatus);
                return invCountHeaderDTO;
            }
        }

        for (InvCountLine lineUpdate : listLine) {
            InvCountLine requestLine = lineRequestMap.get(lineUpdate.getCountLineId());
            lineUpdate.setUnitQty(requestLine.getUnitQty());
            lineUpdate.setUnitDiffQty(lineUpdate.getUnitQty().subtract(lineUpdate.getSnapshotUnitQty()));
            lineUpdate.setRemark(requestLine.getRemark());
            updateList.add(lineUpdate);
        }

        lineRepository.batchUpdateOptional(updateList,
                InvCountLine.FIELD_UNIT_QTY,
                InvCountLine.FIELD_UNIT_DIFF_QTY,
                InvCountLine.FIELD_REMARK);
        invCountHeaderDTO.setCountOrderLineList(updateList);

        invCountHeaderDTO.setStatus(successStatus);

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
                    String errorMsg = "HeaderId " + header.getCountHeaderId() + ", with LineId " + line.getCountLineId() + "error: There are data rows with empty count quantity. Please check the data";
                    errorList.add(errorMsg);
                }
                if (line.getUnitDiffQty().compareTo(BigDecimal.ZERO)!=0) {
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
                .flatMap(headerDTO -> Stream.of(headerDTO.getCounterIds(), headerDTO.getSupervisorIds(), headerDTO.getCreatedBy().toString()))
                .filter(Objects::nonNull)
                .flatMap(ids -> Arrays.stream(ids.split(",")))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        String existUserIds = convertLongToIdsString(existingUserIdSet);

        Map<Long, String> mapRealName = userRepository.selectByIds(existUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            String tenantCode = DetailsHelper.getUserDetails().getTenantNum();
            String departmentName = mapDepartmentName.get(headerDTO.getDepartmentId());
            String warehouseCode = mapWarehouseCode.get(headerDTO.getWarehouseId());

            String counters = "";
            if (headerDTO.getCounterIds() != null) {
                List<Long> listCounterId = convertIdsToListLong(headerDTO.getCounterIds());
                List<String> listRealName = new ArrayList<>();
                for (Long counterId : listCounterId) {
                    String realName = mapRealName.get(counterId);
                    listRealName.add(realName);
                }
                counters = String.join(", ", listRealName);
            }

            String supervisors = "";
            if (headerDTO.getSupervisorIds() != null) {
                List<Long> listSupervisorId = convertIdsToListLong(headerDTO.getSupervisorIds());
                List<String> listRealName = new ArrayList<>();
                for (Long supervisorId : listSupervisorId) {
                    String realName = mapRealName.get(supervisorId);
                    listRealName.add(realName);
                }
                supervisors = String.join(", ", listRealName);
            }

            String materials = "";
            if (headerDTO.getSnapshotMaterialIds() != null) {
                List<Long> listMaterialId = convertIdsToListLong(headerDTO.getSnapshotMaterialIds());
                List<String> listMaterialName = new ArrayList<>();
                for (Long materialId : listMaterialId) {
                    String materialName = mapMaterial.get(materialId).getMaterialName();
                    listMaterialName.add(materialName);
                }
                materials = String.join(", ", listMaterialName);
            }

            //Batch
            String batches = "";
            if (headerDTO.getSnapshotBatchIds() != null) {
                List<Long> listBatchId = convertIdsToListLong(headerDTO.getSnapshotBatchIds());
                List<String> listBatchCode = new ArrayList<>();
                for (Long batchId : listBatchId) {
                    String batchCode = mapBatch.get(batchId).getBatchCode();
                    listBatchCode.add(batchCode);
                }
                batches = String.join(", ", listBatchCode);
            }

            //Lines
            List<InvCountLine> lineList = mapLineHeaders.get(headerDTO.getCountHeaderId());
            List<InvCountLineDTO> lineDTOList = new ArrayList<>();
            for (InvCountLine line : lineList) {
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
            headerDTO.setCreator(mapRealName.get(headerDTO.getCreatedBy()));
            headerDTO.setCounters(counters);
            headerDTO.setSupervisors(supervisors);
            headerDTO.setMaterials(materials);
            headerDTO.setBatches(batches);
            headerDTO.setCountOrderLineListDTO(lineDTOList);
            if (headerDTO.getWorkflowId() != null) {
                List<RunTaskHistory> taskHistories = new ArrayList<>();
                try {
                    taskHistories = workflowClient.approveHistory(headerDTO.getTenantId(), headerDTO.getWorkflowId());
                } catch (Exception e) {

                }
                headerDTO.setApprovalHistory(taskHistories);
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
            requestHeaderMap.put("Authorization", TokenUtils.getToken());
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
            syncMsgExtra.setProgramvalue("");
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

    private void updateLines(List<InvCountLine> lines, Long currentUserId) {
        for (InvCountLine line : lines) {
            List<Long> listCounter = convertIdsToListLong(line.getCounterIds());
            if (!listCounter.contains(currentUserId)) {
                throw new CommonException("This role can't modify the data!");
            }
            if (line.getUnitQty() == null) {
                continue;
            }
            line.setUnitDiffQty(line.getUnitQty().subtract(line.getSnapshotUnitQty()));
            line.setCounterIds(currentUserId.toString());
        }
        lineRepository.batchUpdateOptional(lines,
                InvCountLine.FIELD_UNIT_QTY,
                InvCountLine.FIELD_UNIT_DIFF_QTY,
                InvCountLine.FIELD_COUNTER_IDS);
    }

    @Transactional(propagation = Propagation.NEVER)
    private void insertUpdateExtra(List<InvCountExtra> insertExtraList, List<InvCountExtra> updateExtraList){
        extraRepository.batchInsertSelective(insertExtraList);
        extraRepository.batchUpdateByPrimaryKeySelective(updateExtraList);
    }

    private List<Long> convertIdsToListLong(String ids) {
        return Arrays.stream(ids.split(",")).map(Long::parseLong).collect(Collectors.toList());
    }

    private Set<Long> convertIdsToListSet(String ids) {
        return Arrays.stream(ids.split(",")).map(Long::parseLong).collect(Collectors.toSet());
    }

    private String convertLongToIdsString(List<Long> listId) {
        return listId.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String convertLongToIdsString(Set<Long> listId) {
        return listId.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void validateCountValueSet(InvCountHeader countHeader, List<String> errorList, List<String> validCountDimensions, List<String> validCountTypes, List<String> validCountModes) {
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

    private String buildStockKey(Long tenantId, Long companyId, Long departmentId, Long warehouseId, String materialId) {
        return tenantId + companyId.toString() + departmentId.toString() + warehouseId.toString() + materialId;
    }

    private String buildStockKey(Long tenantId, Long companyId, Long departmentId, Long warehouseId) {
        return tenantId.toString() + companyId.toString() + departmentId.toString() + warehouseId.toString();
    }

    private Set<Long> buildUniqueSet(List<InvCountHeaderDTO> headerDTOS, Function<InvCountHeaderDTO, Long> extractor) {
        return headerDTOS.stream()
                .map(extractor)
                .collect(Collectors.toSet());
    }

    private Map<String, List<InvStock>> queryStockMap(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // Build unique sets for tenantId, companyId, departmentId, warehouseId
        Set<Long> existingTenantIdSet = buildUniqueSet(invCountHeaderDTOS, InvCountHeaderDTO::getTenantId);
        Set<Long> existingCompanyIdSet = buildUniqueSet(invCountHeaderDTOS, InvCountHeaderDTO::getCompanyId);
        Set<Long> existingDepartmentIdSet = buildUniqueSet(invCountHeaderDTOS, InvCountHeaderDTO::getDepartmentId);
        Set<Long> existingWarehouseIdSet = buildUniqueSet(invCountHeaderDTOS, InvCountHeaderDTO::getWarehouseId);

        // Material and Batch IDs are handled differently since they are lists of IDs in the header
        Set<Long> existingMaterialIdSet = invCountHeaderDTOS.stream()
                .map(invCountHeaderDTO -> convertIdsToListLong(invCountHeaderDTO.getSnapshotMaterialIds()))  // Map each DTO to a list of material IDs
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Set<Long> existingBatchIdSet = invCountHeaderDTOS.stream()
                .map(invCountHeaderDTO -> convertIdsToListLong(invCountHeaderDTO.getSnapshotBatchIds()))  // Map each DTO to a list of material IDs
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        // Create condition to query InvStock by the gathered sets
        Condition condition = new Condition(InvStock.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn(InvStock.FIELD_TENANT_ID, existingTenantIdSet)
                .andIn(InvStock.FIELD_COMPANY_ID, existingCompanyIdSet)
                .andIn(InvStock.FIELD_DEPARTMENT_ID, existingDepartmentIdSet)
                .andIn(InvStock.FIELD_WAREHOUSE_ID, existingWarehouseIdSet)
                .andIn(InvStock.FIELD_MATERIAL_ID, existingMaterialIdSet)
                .andIn(InvStock.FIELD_BATCH_ID, existingBatchIdSet);

        // Query stocks with the condition
        List<InvStock> stocks = stockRepository.selectByCondition(condition);

        // Build stockMap based on the stock's tenantId, companyId, warehouseId, and departmentId
        return stocks.stream()
                .collect(Collectors.groupingBy(stock -> {
                    Long departmentId = (stock.getDepartmentId() != null) ? stock.getDepartmentId() : 0L;
                    return buildStockKey(stock.getTenantId(), stock.getCompanyId(), departmentId, stock.getWarehouseId());
                }));
    }
}