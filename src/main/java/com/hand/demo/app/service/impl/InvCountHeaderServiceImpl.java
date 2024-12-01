package com.hand.demo.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.InvCountExtraService;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.AllArgsConstructor;
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
import org.hzero.mybatis.domian.Condition;
import org.springframework.beans.BeanUtils;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 10:19:44
 */
@Service
@AllArgsConstructor
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    private InvCountHeaderRepository invCountHeaderRepository;

    private InvWarehouseRepository invWarehouseRepository;

    private IamCompanyRepository iamCompanyRepository;

    private IamDepartmentRepository iamDepartmentRepository;

    private InvCountLineRepository invCountLineRepository;

    private InvMaterialRepository invMaterialRepository;

    private InvBatchRepository invBatchRepository;

    private InvStockRepository invStockRepository;

    private InvCountExtraRepository invCountExtraRepository;

    private InvCountExtraService invCountExtraService;

    private WorkflowClient workflowClient;

    private ProfileClient profileClient;

    private final LovAdapter lovAdapter;

    private final CodeRuleBuilder codeRuleBuilder;

    private final InvCountLineService invCountLineService;

    private final InterfaceInvokeSdk interfaceInvokeSdk;

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public void saveData(List<InvCountHeaderDTO> invCountHeaders) {
        manualSaveCheck(invCountHeaders);
        List<InvCountHeaderDTO> insertList = invCountHeaders.stream()
                .filter(line -> line.getCountHeaderId() == null)
                .peek(line -> {
                    line.setCountStatus(InvCountHeaderConstants.COUNT_STATUS_DRAFT);
                    line.setDelFlag(0);
                })
                .collect(Collectors.toList());

        Map<String, String> variableMap = new HashMap<>();
        variableMap.put("customSegment", DetailsHelper.getUserDetails().getTenantId().toString());
        List<String> applyHeaderNumbers = codeRuleBuilder.generateCode(insertList.size(), InvCountHeaderConstants.COUNT_NUMBER_CODE_RULE, variableMap);

        for (int i = 0; i < insertList.size(); i++) {
            InvCountHeaderDTO headerDTO = insertList.get(i);
            headerDTO.setCountNumber(applyHeaderNumbers.get(i));
        }

        List<InvCountHeaderDTO> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        List<InvCountHeaderDTO> headerInDatabaseDTO = getHeaderDTOsFromDb(updateList);

        Map<Long, InvCountHeaderDTO> headerById = headerInDatabaseDTO.stream().collect(Collectors.toMap(InvCountHeaderDTO::getCountHeaderId, Function.identity()));

        for (InvCountHeaderDTO headerDTO : updateList) {
            InvCountHeaderDTO currHeader = headerById.get(headerDTO.getCountHeaderId());
            if (currHeader != null) {
                String countStatus = currHeader.getCountStatus();

                if (countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT)) {
                    invCountHeaderRepository.updateOptional(
                            headerDTO,
                            Utils.getNonNullFields(headerDTO,
                                    InvCountHeader.FIELD_COMPANY_ID,
                                    InvCountHeaderDTO.FIELD_DEPARTMENT_ID,
                                    InvCountHeaderDTO.FIELD_WAREHOUSE_ID,
                                    InvCountHeaderDTO.FIELD_COUNT_DIMENSION,
                                    InvCountHeaderDTO.FIELD_COUNT_TYPE,
                                    InvCountHeaderDTO.FIELD_COUNT_MODE,
                                    InvCountHeaderDTO.FIELD_COUNT_TIME_STR,
                                    InvCountHeaderDTO.FIELD_COUNTER_IDS,
                                    InvCountHeaderDTO.FIELD_SUPERVISOR_IDS,
                                    InvCountHeaderDTO.FIELD_SNAPSHOT_MATERIAL_IDS,
                                    InvCountHeaderDTO.FIELD_SNAPSHOT_BATCH_IDS,
                                    InvCountHeaderDTO.FIELD_REMARK
                            )
                    );
                } else if (countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_INCOUNTING)
                        || countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_REJECTED)) {
                    invCountHeaderRepository.updateOptional(headerDTO,
                            Utils.getNonNullFields(headerDTO,
                                    InvCountHeaderDTO.FIELD_REMARK,
                                    InvCountHeaderDTO.FIELD_REASON));
                }
            }
        }

        invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        InvCountHeaderDTO invCountHeaderDTO = invCountHeaderRepository.selectByPrimary(countHeaderId);
        List<InvCountLineDTO> invCountLineDTOList = invCountLineRepository
                .select(InvCountLineDTO.FIELD_COUNT_HEADER_ID, countHeaderId)
                .stream()
                .map(line -> {
                    InvCountLineDTO lineDTO = new InvCountLineDTO();
                    BeanUtils.copyProperties(line, lineDTO);
                    return lineDTO;
                }).collect(Collectors.toList());

//        List<Long> snapshotMaterialIds = parseCommaSeperatedIds(invCountHeaderDTO.getSnapshotMaterialIds().toString());
        List<SnapshotMaterialDTO> snapshotMaterialList = invMaterialRepository
                .selectByIds(invCountHeaderDTO.getSnapshotMaterialIds().toString())
                .stream()
                .map(material -> new SnapshotMaterialDTO()
                        .setId(material.getMaterialId())
                        .setCode(material.getCategoryCode()))
                .collect(Collectors.toList());

        List<SnapshotBatchDTO> snapshotBatchList = invBatchRepository
                .selectByIds(invCountHeaderDTO.getSnapshotBatchIds().toString())
                .stream()
                .map(batch -> new SnapshotBatchDTO()
                        .setId(batch.getBatchId())
                        .setBatchCode(batch.getBatchCode()))
                .collect(Collectors.toList());

        Map<Long, InvWarehouse> invWarehouseById = getWarehouseMappedById(Collections.singletonList(invCountHeaderDTO));

        Integer isWMSWarehouse = invWarehouseById.get(invCountHeaderDTO.getWarehouseId()).getIsWmsWarehouse();

        invCountHeaderDTO
                .setSnapshotMaterialList(snapshotMaterialList)
                .setSnapshotBatchList(snapshotBatchList)
                .setIsWMSwarehouse(isWMSWarehouse)
                .setCountOrderLineList(invCountLineDTOList)
                .setCounterList(getCounters(invCountHeaderDTO.getCounterIds().toString()))
                .setSupervisorList(getSupervisors(invCountHeaderDTO.getSupervisorIds().toString()));
        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        List<String> allowedCountStatusList = getAllowedCountStatusLovValues();
        List<InvCountHeaderDTO> headerInDatabaseDTO = getHeaderDTOsFromDb(headerDTOList);

        Map<Long, InvCountHeaderDTO> headerDTOMap = headerDTOList.stream()
                .collect(Collectors.toMap(InvCountHeaderDTO::getCountHeaderId, Function.identity()));

        for (InvCountHeaderDTO headerDTO : headerInDatabaseDTO) {
            if (headerDTO.getCountHeaderId() == null) {
                continue;
            }

            InvCountHeaderDTO headerDTOFromInput = headerDTOMap.get(headerDTO.getCountHeaderId());

            if (headerDTOFromInput == null) {
                continue;
            }

            String countStatus = headerDTO.getCountStatus();

            if(!countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT) && allowedCountStatusList.contains(countStatus)) {
                setErrorCountInfoError(headerDTOFromInput, infoDTO, "only draft, in counting, rejected, and withdrawn status can be modified");
            } else if(countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT) && !isCreator(headerDTO.getCreatedBy())) {
                setErrorCountInfoError(headerDTOFromInput, infoDTO, "Document in draft status can only be modified by the document creator.");
            } else if (allowedCountStatusList.contains(countStatus)) {
                Map<Long, InvWarehouse> invWarehouseById = getWarehouseMappedById(headerDTOList);

                boolean isWMSWarehouse = invWarehouseById.get(headerDTO.getWarehouseId()).getIsWmsWarehouse() == 1;
                List<Long> parsedSupervisorIds = parseCommaSeperatedIds(headerDTO.getSupervisorIds().toString());
                List<Long> parsedCounterIds = parseCommaSeperatedIds(headerDTO.getCounterIds().toString());
                if(isWMSWarehouse && !isSupervisor(parsedSupervisorIds)) {
                    setErrorCountInfoError(headerDTOFromInput, infoDTO, "The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
                } else if(!isCounter(parsedCounterIds) && !isSupervisor(parsedSupervisorIds) && !isCreator(headerDTO.getCreatedBy())) {
                    setErrorCountInfoError(headerDTOFromInput, infoDTO, "only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn.");
                }
            } else {
                infoDTO.getSuccessList().add(headerDTO);
            }
        }

        Set<InvCountHeaderDTO> errors = infoDTO.getErrorList();
        if (!errors.isEmpty()) {
            throw new CommonException(errors.toString());
        }

        return infoDTO;

    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        List<InvCountHeaderDTO> headerInDatabaseDTO = getHeaderDTOsFromDb(headerDTOList)
                .stream().filter(Objects::nonNull).collect(Collectors.toList());

        Map<Long, InvCountHeaderDTO> headerDTOMap = headerDTOList.stream()
                .collect(Collectors.toMap(InvCountHeaderDTO::getCountHeaderId, Function.identity()));

        for(InvCountHeaderDTO headerDTO : headerInDatabaseDTO) {
            InvCountHeaderDTO headerDTOFromInput = headerDTOMap.get(headerDTO.getCountHeaderId());

            if (headerDTOFromInput == null) {
                continue;
            }
            if (!headerDTO.getCountStatus().equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT)) {
                setErrorCountInfoError(headerDTOFromInput, infoDTO, "Only allow draft status to be deleted");
            } else if (!Utils.getCurrentUser().getUserId().equals(headerDTO.getCreatedBy())) {
                setErrorCountInfoError(headerDTOFromInput, infoDTO, "Only current user is document creator allow delete document");
            }
        }

        Set<InvCountHeaderDTO> errors = infoDTO.getErrorList();
        if (!errors.isEmpty()) {
            throw new CommonException(errors.toString());
        }

        invCountHeaderRepository.batchDeleteByPrimaryKey(new ArrayList<>(headerDTOList));
        return infoDTO;
    }

    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        List<InvCountHeaderDTO> headerDTOsFromDB = getHeaderDTOsFromDb(headerDTOList).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, InvCountHeaderDTO> headerDTOMap = headerDTOList.stream()
                .collect(Collectors.toMap(InvCountHeaderDTO::getCountHeaderId, Function.identity()));


        Condition stockCondition = new Condition(InvStock.class);
        Condition.Criteria stockCriteria = stockCondition.createCriteria();

        for(InvCountHeaderDTO headerDTO : headerDTOsFromDB) {
            InvCountHeaderDTO headerDTOFromInput = headerDTOMap.get(headerDTO.getCountHeaderId());

            if (headerDTOFromInput == null) {
                continue;
            }

            if (!headerDTO.getCountStatus().equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT)) {
                setErrorCountInfoError(headerDTOFromInput, infoDTO, "Only draft status can execute");
            } else if (!isCreator(headerDTO.getCreatedBy())) {
                setErrorCountInfoError(headerDTOFromInput, infoDTO, "Only the document creator can execute");
            }

            valueSetValidation(infoDTO, headerDTO);

            companyDepartmentWarehouseValidation(headerDTOList, headerDTOFromInput, infoDTO);

            List<Long> materialIds = Arrays.stream(
                    headerDTO.getSnapshotMaterialIds()
                            .toString()
                            .split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            stockCriteria
                    .andEqualTo(InvStock.FIELD_TENANT_ID, headerDTO.getTenantId())
                    .andEqualTo(InvStock.FIELD_COMPANY_ID, headerDTO.getCompanyId())
                    .andEqualTo(InvStock.FIELD_DEPARTMENT_ID, headerDTO.getDepartmentId())
                    .andEqualTo(InvStock.FIELD_WAREHOUSE_ID, headerDTO.getWarehouseId())
                    .andIn(InvStock.FIELD_MATERIAL_ID, materialIds)
                    .andNotEqualTo(InvStock.FIELD_UNIT_QUANTITY, 0);
            List<InvStock> stockList = invStockRepository.selectByCondition(stockCondition);

            if (stockList == null || stockList.isEmpty()) {
                setErrorCountInfoError(headerDTO, infoDTO, "Unable to query on hand quantity data");
            }
        }

        Set<InvCountHeaderDTO> errors = infoDTO.getErrorList();
        if (!errors.isEmpty()) {
            throw new CommonException(errors.toString());
        }
        return infoDTO;
    }

    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> headerDTOList) {
        executeCheck(headerDTOList);
        List<InvCountHeaderDTO> headerInDatabaseDTO = getHeaderDTOsFromDb(headerDTOList)
                .stream().filter(Objects::nonNull).collect(Collectors.toList());

        List<InvCountLineDTO> saveCountLine = new ArrayList<>();
        for(InvCountHeaderDTO headerDTO : headerInDatabaseDTO) {
            headerDTO.setCountStatus(InvCountHeaderConstants.COUNT_STATUS_INCOUNTING);
//            InvStockDTO stockDTO = new InvStockDTO()
//                    .setCountingDimension(headerDTO.getCountDimension())
//                    .setSnapshotMaterialIds(headerDTO.getSnapshotMaterialIds())
//                    .setSnapshotBatchIds(headerDTO.getSnapshotBatchIds());
            List<InvStockDTO> summedStockDTOList = invStockRepository.stockTableSum(headerDTO);
            if (!summedStockDTOList.isEmpty()) {
                summedStockDTOList.forEach(stock -> {
                    InvCountLineDTO countLine = (InvCountLineDTO) new InvCountLineDTO()
                            .setTenantId(DetailsHelper.getUserDetails().getTenantId())
                            .setCountHeaderId(headerDTO.getCountHeaderId())
                            .setWarehouseId(stock.getWarehouseId())
                            .setMaterialId(stock.getMaterialId())
                            .setUnitCode(stock.getUnitCode())
                            .setBatchId(stock.getBatchId())
                            .setSnapshotUnitQty(stock.getTotalUnitQuantity())
                            .setCounterIds(headerDTO.getCounterIds());
                    saveCountLine.add(countLine);
                });
            }
        }

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(headerInDatabaseDTO));
        invCountLineService.saveData(new ArrayList<>(saveCountLine));
        return headerInDatabaseDTO;
    }

    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        List<InvCountHeaderDTO> headerInDatabaseDTO = getHeaderDTOsFromDb(headerDTOList)
                .stream().filter(Objects::nonNull).collect(Collectors.toList());

        List<Long> headerIds = headerInDatabaseDTO.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .collect(Collectors.toList());

        Condition headerLineCondition = new Condition(InvCountLine.class);
        Condition.Criteria headerLineCriteria = headerLineCondition.createCriteria();
        headerLineCriteria.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIds);

        List<InvCountLine> lineList = invCountLineRepository.selectByCondition(headerLineCondition);
        Map<Long, InvCountLine> lineByHeaderId = lineList.stream().collect(Collectors.toMap(InvCountLine::getCountHeaderId, Function.identity()));
        List<BigDecimal> unitQtys = lineList.stream().map(InvCountLine::getUnitQty).collect(Collectors.toList());

        for(InvCountHeaderDTO headerDTO : headerInDatabaseDTO) {
            if (!InvCountHeaderConstants.HEADER_COUNT_DESIRED_SUBMISSION_STATUSES.contains(headerDTO.getCountStatus())) {
                setErrorCountInfoError(headerDTO, infoDTO, "The operation is allowed only when the status in in counting, processing, rejected, withdrawn.");
            } else if (!isSupervisor(parseCommaSeperatedIds(headerDTO.getSupervisorIds().toString()))) {
                setErrorCountInfoError(headerDTO, infoDTO, "Only the current login user is the supervisor can submit document.");
            } else if (unitQtys.contains(null)) {
                setErrorCountInfoError(headerDTO, infoDTO, "There are data rows with empty count quantity. Please check the data.");
            }
            InvCountLine line = lineByHeaderId.get(headerDTO.getCountHeaderId());
            if (line != null) {
                if (line.getUnitDiffQty().equals(new BigDecimal(0))) {
                    setErrorCountInfoError(headerDTO, infoDTO, "Reason field must be entered.");
                }
            }
        }
        return infoDTO;
    }

    @Override
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> headerDTOList) {
        List<InvCountHeaderDTO> headerDTOsFromDB = getHeaderDTOsFromDb(headerDTOList).stream().filter(Objects::nonNull).collect(Collectors.toList());

        // get department ids from each header
        String departmentIds = headerDTOsFromDB.stream()
                .map(InvCountHeaderDTO::getDepartmentId)
                .map(String::valueOf)
                .distinct()
                .collect(Collectors.joining(","));

        // get list of departments of the corresponding headers
        List<IamDepartment> departmentList = iamDepartmentRepository.selectByIds(departmentIds);

        // Group department code by department Id
        Map<Long, String> departmentCodeById = departmentList.stream().collect(Collectors.toMap(IamDepartment::getDepartmentId, IamDepartment::getDepartmentCode));

        String workflowFlag = profileClient.getProfileValueByOptions(DetailsHelper.getUserDetails().getTenantId(), null, null,InvCountHeaderConstants.WORKFLOW_PROFILE_CLIENT);

        List<InvCountHeaderDTO> headersToUpdate = new ArrayList<>();

        for(InvCountHeaderDTO headerDTO : headerDTOsFromDB) {
            if (workflowFlag.equals("1")) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put(InvCountHeaderConstants.DEPARTMENT_CODE, departmentCodeById.get(headerDTO.getDepartmentId()));
                workflowClient.startInstanceByFlowKey(
                        headerDTO.getTenantId(),
                        InvCountHeaderConstants.FLOW_KEY,
                        headerDTO.getCountNumber(),
                        InvCountHeaderConstants.DIMENSION,
                        InvCountHeaderConstants.EMPLOYEE_ID.toString(),
                        paramMap
                );
            } else {
                headerDTO.setCountStatus(InvCountHeaderConstants.COUNT_STATUS_CONFIRMED);
                headersToUpdate.add(headerDTO);
            }
        }

        if (!headersToUpdate.isEmpty()) {
            invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(headersToUpdate), InvCountHeaderDTO.FIELD_COUNT_STATUS);
        }
        return headerDTOList;
    }

    @Override
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        Set<Long> warehouseIds = headerDTOList.stream().map(InvCountHeaderDTO::getWarehouseId).collect(Collectors.toSet());
        Set<Long> tenantIds = headerDTOList.stream().map(InvCountHeaderDTO::getTenantId).collect(Collectors.toSet());
        Set<Long> headerIds = headerDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toSet());

        Condition warehouseCondition = new Condition(InvWarehouse.class);
        Condition.Criteria warehouseCriteria = warehouseCondition.createCriteria();
        warehouseCriteria
                .andIn(InvWarehouse.FIELD_WAREHOUSE_ID, warehouseIds)
                .andIn(InvWarehouse.FIELD_TENANT_ID, tenantIds);

        Condition countExtraCondition = new Condition(InvCountExtra.class);
        Condition.Criteria countExtraCriteria = countExtraCondition.createCriteria();
        countExtraCriteria.andIn(InvCountExtra.FIELD_SOURCEID, headerIds);

        List<InvWarehouse> warehouses = invWarehouseRepository.selectByCondition(warehouseCondition);
        List<InvCountExtra> countExtraList = invCountExtraRepository.selectByCondition(countExtraCondition);
        List<InvCountHeaderDTO> headerDTOsFromDb = getHeaderDTOsFromDb(headerDTOList).stream().filter(Objects::nonNull).collect(Collectors.toList());
        List<InvCountLineDTO> headerLines = getLinesByHeaderIds(headerIds);
        List<InvCountExtra> updateCountExtras = new ArrayList<>();
        List<InvCountHeaderDTO> updateCountHeaders = new ArrayList<>();

        Map<Long, List<InvCountExtra>> countExtraByHeaderId = countExtraList.stream().collect(Collectors.groupingBy(InvCountExtra::getSourceid));
        Map<Long, List<InvCountLineDTO>> lineByHeaderId = headerLines.stream().collect(Collectors.groupingBy(InvCountLineDTO::getCountHeaderId));
        Map<Long, InvWarehouse> warehouseById = warehouses.stream().collect(Collectors.toMap(InvWarehouse::getWarehouseId, Function.identity()));
        Map<Long, InvCountHeaderDTO> headerFromDBById = headerDTOsFromDb.stream().collect(Collectors.toMap(InvCountHeaderDTO::getCountHeaderId, Function.identity()));

        for(InvCountHeaderDTO headerDTO : headerDTOList) {
            InvWarehouse headerWarehouse = warehouseById.get(headerDTO.getWarehouseId());
            if (headerWarehouse == null) {
                setErrorCountInfoError(headerDTO, infoDTO, "Warehouse doesn't exist");
                continue;
            }

            List<InvCountExtra> statusExtra = countExtraByHeaderId.getOrDefault(headerDTO.getCountHeaderId(), Collections.emptyList());
            Map<String, InvCountExtra> extraByProgramKey = statusExtra.isEmpty() ? null :
                    statusExtra.stream().collect(Collectors.toMap(InvCountExtra::getProgramkey, Function.identity()));

            InvCountExtra syncStatusExtra = statusExtra.isEmpty()
                    ? new InvCountExtra()
                    .setTenantid(DetailsHelper.getUserDetails().getTenantId())
                    .setSourceid(headerDTO.getCountHeaderId())
                    .setEnabledflag(1)
                    .setProgramkey(InvCountHeaderConstants.WMS_SYNC_PROGRAM_KEY_STATUS)
                    : extraByProgramKey.get(InvCountHeaderConstants.WMS_SYNC_PROGRAM_KEY_STATUS);

            InvCountExtra syncMsgExtra = statusExtra.isEmpty()
                    ? new InvCountExtra()
                    .setTenantid(DetailsHelper.getUserDetails().getTenantId())
                    .setSourceid(headerDTO.getCountHeaderId())
                    .setEnabledflag(1)
                    .setProgramkey(InvCountHeaderConstants.WMS_SYNC_PROGRAM_KEY_ERROR_MSG)
                    : extraByProgramKey.get(InvCountHeaderConstants.WMS_SYNC_PROGRAM_KEY_ERROR_MSG);

            if (headerWarehouse.getIsWmsWarehouse().equals(1)) {
                InvCountHeaderDTO header = headerFromDBById.get(headerDTO.getCountHeaderId());

                if (header != null) {
                    List<InvCountLineDTO> lines = lineByHeaderId.get(header.getCountHeaderId());
                    header.setCountOrderLineList(lines);
                    header.setEmployeeNumber(InvCountHeaderConstants.EMPLOYEE_ID);
                    ResponsePayloadDTO response = callWmsApiPushCountOrder(header);

                    if (response.getBody() == null) {
                        setErrorCountInfoError(headerDTO, infoDTO, "Response body is missing or null. Unable to process the request.");
                        continue;
                    }

                    JSONObject responseBody = JSON.parseObject(response.getPayload());
                    System.out.println("Response Body: " + response.getBody());
                    if (responseBody.getString("returnStatus").equals("S")) {
                        syncStatusExtra.setProgramvalue("SUCCESS");
                        syncMsgExtra.setProgramvalue("");
                        header.setRelatedWmsOrderCode(responseBody.getString("code"));
                        updateCountHeaders.add(header);
                    } else {
                        syncStatusExtra.setProgramvalue("ERROR");
                        syncMsgExtra.setProgramvalue(responseBody.getString("returnMsg"));
                    }

                }
            } else {
                syncMsgExtra.setProgramvalue("");
                syncStatusExtra.setProgramvalue("SKIP");
            }
            updateCountExtras.add(syncStatusExtra);
            updateCountExtras.add(syncMsgExtra);
        }

        if (!updateCountExtras.isEmpty()) {
            invCountExtraService.saveData(updateCountExtras);
//            invCountExtraRepository.batchUpdateByPrimaryKeySelective(updateCountExtras);
        }

        Set<InvCountHeaderDTO> errors = infoDTO.getErrorList();
        if (!errors.isEmpty()) {
            throw new CommonException(errors.toString());
        }

        if (!updateCountHeaders.isEmpty()) {
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateCountHeaders));
        }

        return infoDTO;
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO countHeaderDTO) {
        InvWarehouse warehouse = invWarehouseRepository.selectByPrimary(countHeaderDTO.getWarehouseId());

        if (!warehouse.getIsWmsWarehouse().equals(1)) {
            countHeaderDTO.setErrorMsg("The current warehouse is not a WMS warehouse, operations are not allowed");
            countHeaderDTO.setStatus("E");
            return countHeaderDTO;
        }

        Set<Long> lineIdsFromInput = countHeaderDTO.getCountOrderLineList()
                .stream()
                .map(InvCountLineDTO::getCountLineId)
                .collect(Collectors.toSet());
        Set<Long> lineIdsFromDB = invCountLineRepository.select(InvCountLineDTO.FIELD_COUNT_HEADER_ID, countHeaderDTO.getCountHeaderId())
                .stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toSet());

        if (lineIdsFromInput.size() != lineIdsFromDB.size() || !lineIdsFromInput.equals(lineIdsFromDB)) {
            countHeaderDTO.setErrorMsg("The counting order line data is inconsistent with the INV system, please check the data");
            countHeaderDTO.setStatus("E");
            return countHeaderDTO;
        }

        invCountLineService.saveData(countHeaderDTO.getCountOrderLineList());
        return countHeaderDTO;
    }

    @Override
    public List<InvCountHeaderDTO> countingOrderReportDs(List<InvCountHeaderDTO> headerDTOList) {
        Set<Long> headerIdsLong = headerDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toSet());
        String countHeaderIds = headerIdsLong.stream().map(String::valueOf).collect(Collectors.joining(","));
        // Get all headers
        List<InvCountHeaderDTO> invCountHeaderDTOs = invCountHeaderRepository.selectByIds(countHeaderIds)
                .stream().map(header -> {
                    InvCountHeaderDTO headerDTO = new InvCountHeaderDTO();
                    BeanUtils.copyProperties(header, headerDTO);
                    return headerDTO;
                }).collect(Collectors.toList());

        // Get all snapshot materials
        String allSnapshotMaterialIds = invCountHeaderDTOs.stream()
                .map(header -> header.getSnapshotMaterialIds().toString())
                .collect(Collectors.joining(","));
        List<SnapshotMaterialDTO> allSnapshotMaterials = invMaterialRepository
                .selectByIds(allSnapshotMaterialIds)
                .stream()
                .map(material -> new SnapshotMaterialDTO()
                        .setId(material.getMaterialId())
                        .setCode(material.getCategoryCode()))
                .collect(Collectors.toList());

        // Get all snapshot batches
        String allSnapshotBatchIds = invCountHeaderDTOs.stream()
                .map(header -> header.getSnapshotBatchIds().toString())
                .collect(Collectors.joining(","));
        List<SnapshotBatchDTO> allSnapshotBatches = invBatchRepository
                .selectByIds(allSnapshotBatchIds)
                .stream()
                .map(batch -> new SnapshotBatchDTO()
                        .setId(batch.getBatchId())
                        .setBatchCode(batch.getBatchCode()))
                .collect(Collectors.toList());

        // Group warehouses by ID: {warehouseId: InvWarehouse}
        Map<Long, InvWarehouse> invWarehouseById = getWarehouseMappedById(invCountHeaderDTOs);

        // Get all headers lines
        List<InvCountLineDTO> lineDTOList = invCountLineRepository.selectCountingDetails(new ArrayList<>(headerIdsLong));

        // Group lines by header ID's: {headerId: [InvCountLineDTO1, InvCountLineDTO2, ...]
        Map<Long, List<InvCountLineDTO>> linesByHeaderId = lineDTOList.stream().collect(Collectors.groupingBy(InvCountLineDTO::getCountHeaderId));

        // Process each header
        return invCountHeaderDTOs.stream()
                .map(header -> {
                    // Filter snapshot materials and batches for this header
                    List<SnapshotMaterialDTO> headerMaterials = allSnapshotMaterials.stream()
                            .filter(material -> header.getSnapshotMaterialIds().toString().contains(material.getId().toString()))
                            .collect(Collectors.toList());

                    List<SnapshotBatchDTO> headerBatches = allSnapshotBatches.stream()
                            .filter(batch -> header.getSnapshotBatchIds().toString().contains(batch.getId().toString()))
                            .collect(Collectors.toList());

                    Integer isWMSWarehouse = invWarehouseById.get(header.getWarehouseId()).getIsWmsWarehouse();

                    // Set all properties
                    return header
                            .setSnapshotMaterialList(headerMaterials)
                            .setSnapshotBatchList(headerBatches)
                            .setIsWMSwarehouse(isWMSWarehouse)
                            .setCountOrderLineList(linesByHeaderId.getOrDefault(header.getCountHeaderId(), new ArrayList<>()))
                            .setCounterList(getCounters(header.getCounterIds().toString()))
                            .setSupervisorList(getSupervisors(header.getSupervisorIds().toString()));
                })
                .collect(Collectors.toList());
    }

    private ResponsePayloadDTO callWmsApiPushCountOrder(InvCountHeaderDTO header) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setPayload(JSON.toJSONString(header));
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("Authorization", "bearer 2bf9fd99-458f-4e3b-8bad-109b9a95b9df");
        requestPayloadDTO.setHeaderParamMap(paramMap);
        requestPayloadDTO.setMediaType("application/json");
        return interfaceInvokeSdk.invoke(
                InvCountHeaderConstants.EXTERNAL_WMS_SERVICE_NAMESPACE,
                InvCountHeaderConstants.EXTERNAL_WMS_SERVICE_SERVER_CODE,
                InvCountHeaderConstants.EXTERNAL_WMS_SERVICE_INTERFACE_CODE,
                requestPayloadDTO
        );
    }

    private List<CounterDTO> getCounters(String counterIds) {
        List<String> ids = Arrays.stream(counterIds.split(",")).map(String::trim).collect(Collectors.toList());
        return ids.stream().map(id -> new CounterDTO().setId(Long.parseLong(id))).collect(Collectors.toList());
    }

    private List<SupervisorDTO> getSupervisors(String buyerIds) {
        List<String> ids = Arrays.asList(buyerIds.split(","));
        return ids.stream().map(id -> new SupervisorDTO().setId(Long.parseLong(id))).collect(Collectors.toList());
    }

    private List<String> getAllowedCountStatusLovValues() {
        return lovAdapter.queryLovValue(InvCountHeaderConstants.INV_COUNT_HEADER_COUNT_STATUS, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .filter(InvCountHeaderConstants.HEADER_COUNT_DESIRED_STATUSES::contains)
                .collect(Collectors.toList());
    }

    private List<String> getCountStatusLovValues() {
        return lovAdapter.queryLovValue(InvCountHeaderConstants.INV_COUNT_HEADER_COUNT_STATUS, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private List<String> getCountDimensionLovValues() {
        return lovAdapter.queryLovValue(InvCountHeaderConstants.INV_COUNT_HEADER_COUNT_DIMENSION, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private List<String> getCountTypeLovValues() {
        return lovAdapter.queryLovValue(InvCountHeaderConstants.INV_COUNT_HEADER_COUNT_TYPE, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private List<String> getCountModeLovValues() {
        return lovAdapter.queryLovValue(InvCountHeaderConstants.INV_COUNT_HEADER_COUNT_MODE, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private void companyDepartmentWarehouseValidation(List<InvCountHeaderDTO> headerListFromInput, InvCountHeaderDTO headerDTOFromInput, InvCountInfoDTO infoDTO) {
        String companyIds = headerListFromInput.stream()
                .map(InvCountHeaderDTO::getCompanyId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String departmentIds = headerListFromInput.stream()
                .map(InvCountHeaderDTO::getDepartmentId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String warehouseIds = headerListFromInput.stream()
                .map(InvCountHeaderDTO::getWarehouseId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        List<Long> companyIdsFromDB = iamCompanyRepository.selectByIds(companyIds).stream()
                .map(IamCompany::getCompanyId)
                .collect(Collectors.toList());

        List<Long> departmentIdsFromDB = iamDepartmentRepository.selectByIds(departmentIds).stream()
                .map(IamDepartment::getDepartmentId)
                .collect(Collectors.toList());

        List<Long> warehouseIdsFromDB = invWarehouseRepository.selectByIds(warehouseIds).stream()
                .map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toList());

        if (!companyIdsFromDB.contains(headerDTOFromInput.getCompanyId())) {
            setErrorCountInfoError(headerDTOFromInput, infoDTO, "Company not existed");
        }
        if (!departmentIdsFromDB.contains(headerDTOFromInput.getDepartmentId())) {
            setErrorCountInfoError(headerDTOFromInput, infoDTO, "Department not existed");
        }
        if (!warehouseIdsFromDB.contains(headerDTOFromInput.getWarehouseId())) {
            setErrorCountInfoError(headerDTOFromInput, infoDTO, "Warehouse not existed");
        }
    }

    private void valueSetValidation(InvCountInfoDTO infoDTO, InvCountHeaderDTO headerDTO) {
        List<String> countStatusLov = getCountStatusLovValues();
        List<String> countDimensionLov = getCountDimensionLovValues();
        List<String> countTypeLov = getCountTypeLovValues();
        List<String> countModeLov = getCountModeLovValues();

        if (!countStatusLov.contains(headerDTO.getCountStatus())) {
            setErrorCountInfoError(headerDTO, infoDTO, "Count status not valid");
        }

        if (!countDimensionLov.contains(headerDTO.getCountDimension())) {
            setErrorCountInfoError(headerDTO, infoDTO, "Count dimension not valid");
        }

        if (!countTypeLov.contains(headerDTO.getCountType())) {
            setErrorCountInfoError(headerDTO, infoDTO, "Count type not valid");
        }

        if (!countModeLov.contains(headerDTO.getCountMode())) {
            setErrorCountInfoError(headerDTO, infoDTO, "Count mode not valid");
        }
    }

    private Map<Long, InvWarehouse> getWarehouseMappedById(List<InvCountHeaderDTO> headerDTOList) {
        List<InvCountHeaderDTO> updateList = headerDTOList.stream()
                .filter(header -> header.getCountHeaderId() != null)
                .collect(Collectors.toList());

        Set<Long> invWarehouseIdsInHeaders = updateList.stream()
                .map(InvCountHeaderDTO::getWarehouseId)
                .collect(Collectors.toSet());

        Condition warehouseCondition = new Condition(InvWarehouse.class);
        Condition.Criteria warehouseCriteria = warehouseCondition.createCriteria();
        warehouseCriteria.andIn(InvWarehouse.FIELD_WAREHOUSE_ID, invWarehouseIdsInHeaders);

        List<InvWarehouse> invWarehouseList = invWarehouseRepository.selectByCondition(warehouseCondition);

        return invWarehouseList.stream().collect(Collectors.toMap(InvWarehouse::getWarehouseId, Function.identity()));
    }

    private List<Long> parseCommaSeperatedIds(String ids) {
        return Arrays.stream(ids.split(",")).map(Long::parseLong).collect(Collectors.toList());
    }

    private boolean isSupervisor(List<Long> supervisorIds) {
        return supervisorIds.contains(Utils.getCurrentUser().getUserId());
    }

    private boolean isCounter(List<Long> counterIds) {
        return counterIds.contains(Utils.getCurrentUser().getUserId());
    }

    private boolean isCreator(Long creator) {
        return Utils.getCurrentUser().getUserId().equals(creator);
    }

    private void setErrorCountInfoError(InvCountHeaderDTO headerDTO, InvCountInfoDTO infoDTO, String errorMessage) {
        headerDTO.getErrorMessageList().add(errorMessage);
        infoDTO.getErrorList().add(headerDTO);
    }

    private List<InvCountHeaderDTO> getHeaderDTOsFromDb(List<InvCountHeaderDTO> headerList) {
        Set<Long> headerIds = headerList.stream()
                .map(header -> (header != null) ? header.getCountHeaderId() : null)
                .collect(Collectors.toSet());

        List<InvCountHeader> headerInDatabase = new ArrayList<>();
        if (!headerIds.isEmpty()) {
            Condition headerCondition = new Condition(InvCountHeader.class);
            Condition.Criteria headerCriteria = headerCondition.createCriteria();
            headerCriteria.andIn(InvCountHeaderDTO.FIELD_COUNT_HEADER_ID, headerIds);
            headerInDatabase = invCountHeaderRepository.selectByCondition(headerCondition);
        }

        return headerInDatabase.stream()
                .map(header -> {
                    InvCountHeaderDTO countHeaderDTO = new InvCountHeaderDTO();
                    BeanUtils.copyProperties(header, countHeaderDTO);
                    return countHeaderDTO;
                })
                .collect(Collectors.toList());
    }

    private List<InvCountLineDTO> getLinesByHeaderIds(Set<Long> headerIds) {
        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIds);

        return invCountLineRepository.selectByCondition(condition)
                .stream()
                .map(line -> {
                    InvCountLineDTO lineDTO = new InvCountLineDTO();
                    BeanUtils.copyProperties(line, lineDTO);
                    return lineDTO;
                }).collect(Collectors.toList());

    }

    private List<RunTaskHistory> getApproveHistory(Long tenantId, String businessKey) {
        return workflowClient.approveHistoryByFlowKey(tenantId, InvCountHeaderConstants.FLOW_KEY, businessKey);
    }
}

