package com.hand.demo.app.service.impl;

import org.json.JSONObject;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.controller.v1.DTO.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountHeaderConstant;
import com.netflix.discovery.converters.Auto;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.ds.strategy.In;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.cache.ProcessCacheValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import static com.sun.corba.se.impl.util.RepositoryId.cache;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 08:42:19
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Autowired
    private InvCountLineRepository invCountLineRepository;

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

    @Autowired
    private InvCountExtraRepository invCountExtraRepository;

    @Autowired
    private InterfaceInvokeSdk interfaceInvokeSdk;

    @Autowired
    LovAdapter lovAdapter;

    @Autowired
    IamRemoteService iamRemoteService;

    @Autowired
    CodeRuleBuilder codeRuleBuilder;

    private static final Logger log = LoggerFactory.getLogger(InvCountHeaderServiceImpl.class);

    @Override
    public Page<List<InvCountHeaderDTO>> countingOrderQueryList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO) {
        InvCountHeader invCountHeader = new InvCountHeader();
        BeanUtils.copyProperties(invCountHeaderDTO, invCountHeader);

        Page<InvCountHeader> pageResult = PageHelper.doPageAndSort(pageRequest, () -> {
            invCountHeader.setSupervisorIds(invCountHeaderDTO.getSupervisorId());
            return invCountHeaderRepository.selectList(invCountHeader);
        });

        List<InvCountHeaderDTO> invCountHeaderDTOList = new ArrayList<>();
        for (InvCountHeader entity : pageResult) {
            InvCountHeaderDTO dto = new InvCountHeaderDTO();
            BeanUtils.copyProperties(entity, dto);
            invCountHeaderDTOList.add(dto);
        }

        Page<List<InvCountHeaderDTO>> invoiceApplyHeadersDTOPage = new Page<>();
        invoiceApplyHeadersDTOPage.setContent(Collections.singletonList(invCountHeaderDTOList));
        invoiceApplyHeadersDTOPage.setTotalPages(pageResult.getTotalPages());
        invoiceApplyHeadersDTOPage.setTotalElements(pageResult.getTotalElements());
        invoiceApplyHeadersDTOPage.setNumber(pageResult.getNumber());
        invoiceApplyHeadersDTOPage.setSize(pageResult.getSize());

        return invoiceApplyHeadersDTOPage;
    }

    public InvCountInfoDTO countingOrderRemove(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        JSONObject jsonObject = new org.json.JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

        List<InvCountHeader> invCountHeaderList = new ArrayList<>();
        Set<String> errorSet = new HashSet<>();

        for (InvCountHeader invCountHeader : invCountHeaders) {
            String countStatus = invCountHeader.getCountStatus();
            Long createdBy = invCountHeader.getCreatedBy();
            InvCountHeader invCountHeaderNew = new InvCountHeader();

            if (!"DRAFT".equals(countStatus)) {
                errorSet.add("error1");
            }
            if (!userId.equals(createdBy)) {
                errorSet.add("error2");
            }
            if ("DRAFT".equals(countStatus) && userId.equals(createdBy)) {
                invCountHeaderNew.setCountHeaderId(invCountHeader.getCountHeaderId());
                invCountHeaderList.add(invCountHeaderNew);
            }
        }

        List<String> errorMsg = new ArrayList<>(errorSet);
        List<String> errorMessage = new ArrayList<>();
        if(errorMsg.contains("error1")) {
            errorMessage.add("Only allow draft status to be deleted");
        }
        if(errorMsg.contains("error2")) {
            errorMessage.add("Only current user is document creator allow delete document");
        }

        invCountInfoDTO.setErrorMessage(errorMessage);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        } else {
            invCountInfoDTO.setErrorMessage(null);
            invCountInfoDTO.setInvCountHeaderDTOList(invCountHeaders);
            invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaderList);
        }
        return invCountInfoDTO;
    }

    @ProcessCacheValue
    public InvCountHeaderDTO countingOrderQueryDetail(Long countHeaderId) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);

        String counterIds = invCountHeader.getCounterIds();
        List<String> counterList = Arrays.asList(counterIds.split(","));
        List<UserCacheDTO> counterListMap = new ArrayList<>();

        for(String counterListItem : counterList) {
            UserCacheDTO userCacheDTO = new UserCacheDTO();
            userCacheDTO.setId(Long.parseLong(counterListItem));
            counterListMap.add(userCacheDTO);
        }

        String supervisorIds = invCountHeader.getSupervisorIds();
        List<String> supervisorList = Arrays.asList(supervisorIds.split(","));
        List<UserCacheDTO> supervisorListMap = new ArrayList<>();

        for(String supervisorItem : supervisorList) {
            UserCacheDTO userCacheDTO = new UserCacheDTO();
            userCacheDTO.setId(Long.parseLong(supervisorItem));
            supervisorListMap.add(userCacheDTO);
        }

        String snapshotMaterials = invCountHeader.getSnapshotMaterialIds();
        List<Map<String, Object>> invMaterialMap = invMaterialRepository.selectByIds(snapshotMaterials).stream()
                .map(material -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", material.getMaterialId());
                    map.put("code", material.getMaterialCode());
                    return map;
                })
                .collect(Collectors.toList());

        String snapshotBatchs = invCountHeader.getSnapshotBatchIds();
        List<Map<String, Object>> invBatchMap = invBatchRepository.selectByIds(snapshotBatchs).stream()
                .map(batch -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", batch.getBatchId());
                    map.put("code", batch.getBatchCode());
                    return map;
                })
                .collect(Collectors.toList());

        Long warehouseId = invCountHeader.getWarehouseId();
        InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(warehouseId);

        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);
        invCountHeaderDTO.setCounterList(counterListMap);
        invCountHeaderDTO.setSupervisorList(supervisorListMap);
        invCountHeaderDTO.setSnapshotMaterialList(invMaterialMap);
        invCountHeaderDTO.setSnapshotBatchList(invBatchMap);
        invCountHeaderDTO.setIsWMSwarehouse(invWarehouse.getIsWmsWarehouse());

        InvCountLine invCountLineNew = new InvCountLine();
        invCountLineNew.setCountHeaderId(countHeaderId);
        List<InvCountLine> invCountLineList = invCountLineRepository.select(invCountLineNew);
        List<InvCountLineDTO> invCountLineDTOList = invCountLineList.stream()
                .sorted((line1, line2) -> line2.getCreationDate().compareTo(line1.getCreationDate()))
                .map(invCountLine -> {
                    InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
                    BeanUtils.copyProperties(invCountLine, invCountLineDTO);
                    return invCountLineDTO;
                })
                .collect(Collectors.toList());

        if(invCountLineList.size() > 0) {
            invCountHeaderDTO.setCountOrderLineList(invCountLineDTOList);
        }

        return invCountHeaderDTO;
    }

    private InvWarehouse checkWarehouseExists(Long tenantId, Long warehouseId) {
        InvWarehouse invWarehouse = new InvWarehouse();
        invWarehouse.setTenantId(tenantId);
        invWarehouse.setWarehouseId(warehouseId);

        List<InvWarehouse> invWarehouseList = invWarehouseRepository.select(invWarehouse);
        if (invWarehouseList != null && !invWarehouseList.isEmpty()) {
            return invWarehouseList.get(0);
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public InvCountInfoDTO countingOrderSynchronizeWMS(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        List<InvCountInfoDTO> resultList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();

        List<InvCountExtra> invCountExtraListInsert = new ArrayList<>();
        List<InvCountExtra> invCountExtraListUpdate = new ArrayList<>();
        List<InvCountHeader> invCountHeaderListUpdate = new ArrayList<>();

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        // Process each header
        for (InvCountHeaderDTO invCountHeader : invCountHeaderDTOList) {
            Long countHeaderId = invCountHeader.getCountHeaderId();

            // Validate warehouse existence
            InvWarehouse checkWarehouseExists = checkWarehouseExists(invCountHeader.getTenantId(), invCountHeader.getWarehouseId());
            if (checkWarehouseExists == null) {
                errorList.add("Warehouse data not found for warehouseCode: " + invCountHeader.getWarehouseId());
                invCountInfoDTO.setErrorMessage(errorList);
                continue;
            }

            invCountInfoDTO.setInvCountHeaderDTOList(invCountHeaderDTOList);

            // Retrieve extra data
            InvCountExtra invCountExtra = new InvCountExtra();
            invCountExtra.setSourceid(countHeaderId);
            invCountExtra.setEnabledflag(BaseConstants.Flag.YES);

            List<InvCountExtra> invCountExtraList = invCountExtraRepository.select(invCountExtra);

            // If extra data exists, map it
            Map<String, InvCountExtra> invCountExtraMap = invCountExtraList.stream()
                    .collect(Collectors.toMap(InvCountExtra::getProgramkey, extra -> extra));

            InvCountExtra syncStatusExtra = invCountExtraMap.getOrDefault("wms_sync_status", createSyncExtra(invCountHeader, "wms_sync_status"));
            InvCountExtra syncMsgExtra = invCountExtraMap.getOrDefault("wms_sync_error_message", createSyncExtra(invCountHeader, "wms_sync_error_message"));

            // If warehouse is WMS-enabled
            if (Integer.valueOf(1).equals(checkWarehouseExists.getIsWmsWarehouse())) {
                // Fetch and map lines
                InvCountLine invCountLine = new InvCountLine();
                invCountLine.setCountHeaderId(invCountHeader.getCountHeaderId());
                List<InvCountLine> invCountLineList = invCountLineRepository.select(invCountLine);
                List<InvCountLineDTO> invCountLineDTOList = invCountLineList.stream()
                        .map(line -> mapToDTO(line))
                        .collect(Collectors.toList());

                invCountHeader.setCountHeaderId(countHeaderId);
                invCountHeader.setCountOrderLineList(invCountLineDTOList);
                invCountHeader.setTenantId(BaseConstants.DEFAULT_TENANT_ID);
                invCountHeader.setEmployeeNumber("47356");

                Map<String,String> mapParam = new HashMap<>();
                mapParam.put("Authorization", invCountHeader.getAuthorization());

                // Prepare payload and invoke external service
                RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
                requestPayloadDTO.setPayload(JSON.toJSONString(invCountHeader));
                requestPayloadDTO.setHeaderParamMap(mapParam);
                requestPayloadDTO.setMediaType("application/json");

                ResponsePayloadDTO responsePayloadDTO = interfaceInvokeSdk.invoke("HZERO", "FEXAM_WMS", "fexam-wms-api.thirdAddCounting", requestPayloadDTO);
                String payload = responsePayloadDTO.getPayload();
                JSONObject response = new JSONObject(payload);

                InvCountHeader invCountHeaderNew = new InvCountHeader();
                invCountHeaderNew.setCountHeaderId(invCountHeader.getCountHeaderId());

                if ("S".equals(response.getString("returnStatus"))) {
                    syncStatusExtra.setProgramvalue("SUCCESS");
                    syncMsgExtra.setProgramvalue("");
                    invCountHeaderNew.setRelatedWmsOrderCode(response.getString("code"));
                    invCountHeaderListUpdate.add(invCountHeaderNew);
                } else {
                    syncStatusExtra.setProgramvalue("ERROR");
                    syncMsgExtra.setProgramvalue(response.getString("returnMsg"));
                }
            } else {
                // Set status as skipped
                syncStatusExtra.setProgramvalue("SKIP");
                syncMsgExtra.setProgramvalue("SKIP");
            }

            // Insert or update sync data
            if (invCountExtraList.isEmpty()) {
                invCountExtraListInsert.add(syncStatusExtra);
                invCountExtraListInsert.add(syncMsgExtra);
            } else {
                invCountExtraListUpdate.add(syncStatusExtra);
                invCountExtraListUpdate.add(syncMsgExtra);
            }
        }

        // Batch insert and update
        invCountExtraRepository.batchInsertSelective(invCountExtraListInsert);
        invCountExtraRepository.batchUpdateByPrimaryKeySelective(invCountExtraListUpdate);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaderListUpdate);

        return invCountInfoDTO;
    }

    // Helper method to create InvCountExtra for sync status or message
    private InvCountExtra createSyncExtra(InvCountHeader invCountHeader, String programKey) {
        return new InvCountExtra()
                .setTenantid(invCountHeader.getTenantId())
                .setSourceid(invCountHeader.getCountHeaderId())
                .setEnabledflag(BaseConstants.Flag.YES)
                .setProgramkey(programKey);
    }

    // Helper method to map InvCountLine to DTO
    private InvCountLineDTO mapToDTO(InvCountLine invCountLine) {
        InvCountLineDTO dto = new InvCountLineDTO();
        BeanUtils.copyProperties(invCountLine, dto);
        return dto;
    }


    public List<Long> validateLineIdByRequest(InvCountHeaderDTO invCountHeaderDTO) {
        List<InvCountLineDTO> countOrderLineList = invCountHeaderDTO.getCountOrderLineList();
        List<Long> lineIdRequest = countOrderLineList.stream()
                .map(InvCountLineDTO::getCountLineId)
                .collect(Collectors.toList());

        InvCountLine invCountLine = new InvCountLine();
        invCountLine.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
        List<InvCountLine> invCountLineDb = invCountLineRepository.select(invCountLine);

        List<Long> lineIdDb = invCountLineDb.stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toList());

        List<Long> missingLineIds = lineIdRequest.stream()
                .filter(lineId -> !lineIdDb.contains(lineId))
                .collect(Collectors.toList());
        return missingLineIds;
    }

    public InvCountHeaderDTO countingResultSynchronous(InvCountHeaderDTO invCountHeaderDTO) {
        InvCountHeader invCountHeaderDb = invCountHeaderRepository.selectByPrimary(invCountHeaderDTO.getCountHeaderId());

        JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

        InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(invCountHeaderDb.getWarehouseId());
        List<String> errorMsg = new ArrayList<>();
        if (invWarehouse == null || !Integer.valueOf(1).equals(invWarehouse.getIsWmsWarehouse())) {
            errorMsg.add("The current warehouse is not a WMS warehouse, operations are not allowed");
        }

        List<Long> missingLineIds = validateLineIdByRequest(invCountHeaderDTO);
        if (!missingLineIds.isEmpty()) {
            errorMsg.add("The counting order line data is inconsistent with the INV system, please check the data");
        }

        if(errorMsg.size() > 0) {
            InvCountHeaderDTO invCountHeaderDTONew = new InvCountHeaderDTO();
            invCountHeaderDTONew.setErrorMsg(errorMsg);
            invCountHeaderDTO.setStatus("E");

            return invCountHeaderDTONew;
        }

        List<InvCountLineDTO> invCountLineList = invCountHeaderDTO.getCountOrderLineList();
        List<Long> lineIds = invCountLineList.stream().map(InvCountLineDTO::getCountLineId).collect(Collectors.toList());
        String joineLineIds = String.join(",", lineIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountLine> invCountLineMap = invCountLineRepository.selectByIds(joineLineIds).stream()
                .collect(Collectors.toMap(InvCountLine::getCountLineId, header -> header));

        List<InvCountLine> invCountLineUpdateList = new ArrayList<>();
        for(InvCountLine invCountLines : invCountLineList) {
            InvCountLine invCountLineData = invCountLineMap.get(invCountLines.getCountLineId());
            Object unitQty = invCountLines.getUnitQty();
            Object snapshotUnitQty = invCountLineData.getSnapshotUnitQty();

            Double unitQtyDouble = Double.valueOf(unitQty.toString());
            Double snapshotUnitQtyDouble = Double.valueOf(snapshotUnitQty.toString());

            invCountLines.setCountHeaderId(invCountLineData.getCountHeaderId());
            invCountLines.setUnitDiffQty(unitQtyDouble - snapshotUnitQtyDouble);
            invCountLineUpdateList.add(invCountLines);
        }

        invCountLineRepository.batchUpdateByPrimaryKeySelective(invCountLineUpdateList);
        return invCountHeaderDTO;
    }

    public InvCountInfoDTO validateSubmit(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

        List<Long> headerIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

        List<Long> lineIds = invCountHeaderDTOList.stream()
                .flatMap(invCountHeaderDTO -> invCountHeaderDTO.getCountOrderLineList().stream())
                .map(InvCountLineDTO::getCountLineId)
                .collect(Collectors.toList());

        String joinLineId = String.join(",", lineIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));

        List<InvCountLine> invCountLineList = invCountLineRepository.selectByIds(joinLineId);
        Map<Long, List<InvCountLine>> invCountLineMapByHeaderId = invCountLineList.stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));

        List<String> verificationStatus = new ArrayList<>();
        verificationStatus.add("INCOUNTING");
        verificationStatus.add("PROCESSING");
        verificationStatus.add("REJECTED");
        verificationStatus.add("WITHDRAWN");

        Set<String> errorMsg = new HashSet<>();
        for(InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOList) {
            InvCountHeader invCountHeaderDb = invCountHeaderMap.get(invCountHeaderDTO.getCountHeaderId());
            List<InvCountLine> invCountLineDb = invCountLineMapByHeaderId.get(invCountHeaderDTO.getCountHeaderId());

            if (!verificationStatus.contains(invCountHeaderDb.getCountStatus())) {
                errorMsg.add("error1");
            }

            if(!userId.equals(invCountHeaderDb.getSupervisorIds())) {
                errorMsg.add("error2");
            }

            boolean hasZeroUnitQty = invCountLineDb.stream()
                    .anyMatch(invCountLine -> {
                        if (invCountLine.getUnitQty() != null) {
                            if (invCountLine.getUnitQty() instanceof Number) {
                                return ((Number) invCountLine.getUnitQty()).doubleValue() == 0;
                            }
                        }
                        return false;
                    });

            if (hasZeroUnitQty) {
                errorMsg.add("error3");
            }

            List<InvCountLineDTO> invCountLines = invCountHeaderDTO.getCountOrderLineList();
            List<String> errorMessages = invCountLines.stream()
                    .filter(invCountLine -> invCountLine.getUnitDiffQty() == null)
                    .map(invCountLine -> "error4")
                    .collect(Collectors.toList());

            if (!errorMessages.isEmpty()) {
                errorMsg.addAll(errorMessages);
            }
        }

        List<String> errorMessage = new ArrayList<>(errorMsg);
        List<String> errorMeaning = new ArrayList<>();
        if(errorMsg != null || errorMsg.size() > 0) {
            if(errorMessage.contains("error1")) {
                errorMeaning.add("The operation is allowed only when the status in in counting, processing, rejected, withdrawn.");
            }
            if(errorMessage.contains("error2")) {
                errorMeaning.add("Only the current login user is the supervisor can submit document.");
            }
            if(errorMessage.contains("error3")) {
                errorMeaning.add("There are data rows with empty count quantity. Please check the data.");
            }
            if(errorMessage.contains("error4")) {
                errorMeaning.add("the reason field must be entered.");
            }
        }

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        if(errorMeaning.size() > 0 || errorMeaning != null) {
            invCountInfoDTO.setErrorMessage(errorMeaning);
        }
        return invCountInfoDTO;
    }

    public List<InvCountHeaderDTO> countingOrderSubmit(List<InvCountHeaderDTO> invCountHeaderDTOList) {
//        validation data
        InvCountInfoDTO invCountInfoDTO = validateSubmit(invCountHeaderDTOList);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        List<InvCountHeaderDTO> invCountHeaderDTOListNew = new ArrayList<>();
        return invCountHeaderDTOListNew;
    }

    public InvCountInfoDTO countingOrderExecuteVerification(List<InvCountHeaderDTO> invCountHeaderDTOList) {
// create invCountHeader map db by id
        List<Long> headerIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));
// create company map db by id
        List<Long> companyIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getCompanyId).collect(Collectors.toList());
        String joinCompanyId = String.join(",", companyIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, IamCompany> companyMapById = iamCompanyRepository.selectByIds(joinCompanyId).stream()
                .collect(Collectors.toMap(IamCompany::getCompanyId, header -> header));
// create department map db by id
        List<Long> departemenIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getDepartmentId).collect(Collectors.toList());
        String joinDepartemenId = String.join(",", departemenIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, IamDepartment> departmentMapById = iamDepartmentRepository.selectByIds(joinDepartemenId).stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, header -> header));
// create warehouse map db by id
        List<Long> warehouseIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getWarehouseId).collect(Collectors.toList());
        String joinWarehouseId = String.join(",", warehouseIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvWarehouse> warehouseMapById = invWarehouseRepository.selectByIds(joinWarehouseId).stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, header -> header));

        List<LovValueDTO> countApplyStatus = lovAdapter.queryLovValue(InvCountHeaderConstant.COUNT_STATUS, BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> countDimension = lovAdapter.queryLovValue(InvCountHeaderConstant.COUNT_DIMENSION, BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> countType = lovAdapter.queryLovValue(InvCountHeaderConstant.COUNT_TYPE, BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> countMode = lovAdapter.queryLovValue(InvCountHeaderConstant.COUNT_MODE, BaseConstants.DEFAULT_TENANT_ID);

        List<String> applyStatus = countApplyStatus.stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> dimension = countDimension.stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> type = countType.stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> mode = countMode.stream().map(LovValueDTO::getValue).collect(Collectors.toList());

        Set<String> errorMsg = new HashSet<>();
        for(InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOList) {
            InvCountHeader invCountHeaderDb = invCountHeaderMap.get(invCountHeaderDTO.getCountHeaderId());

            JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
            Long userId = jsonObject.getLong("id");

            if (!"DRAFT".equals(invCountHeaderDTO.getCountStatus())) {
                errorMsg.add("error1");
            }
            if (!userId.equals(invCountHeaderDb.getCreatedBy())) {
                errorMsg.add("error2");
            }
            if(!applyStatus.contains(invCountHeaderDTO.getCountStatus()) || !dimension.contains(invCountHeaderDTO.getCountDimension()) || !type.contains(invCountHeaderDTO.getCountType()) || !mode.contains(invCountHeaderDTO.getCountMode())) {
                errorMsg.add("error3");
            }

            String countTimeStr = invCountHeaderDTO.getCountTimeStr();
            if("MONTH".equalsIgnoreCase(invCountHeaderDTO.getCountType())) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
                    formatter.parse(countTimeStr);
                } catch (DateTimeParseException e) {
                    errorMsg.add("error3");
                }
            } else if("YEAR".equalsIgnoreCase(invCountHeaderDTO.getCountType())) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");
                    formatter.parse(countTimeStr);
                } catch (DateTimeParseException e) {
                    errorMsg.add("error3");
                }
            }

            if(companyMapById.get(invCountHeaderDTO.getCompanyId()) == null || departmentMapById.get(invCountHeaderDTO.getDepartmentId()) == null || warehouseMapById.get(invCountHeaderDTO.getWarehouseId()) == null) {
                errorMsg.add("error4");
            }

            InvCountHeader invCountHeader = invCountHeaderMap.get(invCountHeaderDTO.getCountHeaderId());
            String materialIds = invCountHeader.getSnapshotMaterialIds();
            List<Long> materialList = Arrays.stream(materialIds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invCountHeaderDTO.setMaterialList(materialList);

            invCountHeaderDTO.setDepartmentId(invCountHeader.getDepartmentId());

            List<InvStock> invStockList = invStockRepository.checkOnHandQuantity(invCountHeaderDTO);

            if(invStockList.size() == 0) {
                errorMsg.add("error5");
            }
        }

        List<String> errorInitial = new ArrayList<>(errorMsg);
        List<String> errorMessage = new ArrayList<>();
        if(errorInitial.contains("error1")) {
            errorMessage.add("Only draft status can execute.");
        }
        if(errorInitial.contains("error2")) {
            errorMessage.add("Only the document creator can execute.");
        }
        if(errorInitial.contains("error3")) {
            errorMessage.add("Invalid value set.");
        }
        if(errorInitial.contains("error4")) {
            errorMessage.add("Invalid company or department or warehouse.");
        }
        if(errorInitial.contains("error5")) {
            errorMessage.add("Unable to query on hand quantity data.");
        }

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        invCountInfoDTO.setErrorMessage(errorMessage);
        return invCountInfoDTO;
    }

    public List<InvCountHeaderDTO> countingOrderExecute(List<InvCountHeaderDTO> invCountHeaders) {
//        //      validation data
//        InvCountInfoDTO invCountInfoDTO = executeCheck(invCountHeaders);
//        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
//            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
//        }

        // create invCountHeader map db by id
        List<Long> headerIds = invCountHeaders.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

        List<InvCountHeader> invCountHeaderUpdateList = new ArrayList<>();
        List<InvCountLine> invCountLineInsertList = new ArrayList<>();

        for(InvCountHeaderDTO invCountHeader : invCountHeaders) {
            InvCountHeader invCountHeaderNew = new InvCountHeader();
            invCountHeaderNew.setCountHeaderId(invCountHeader.getCountHeaderId());
            invCountHeaderNew.setCountStatus("INCOUNTING");
            invCountHeaderNew.setObjectVersionNumber(invCountHeader.getObjectVersionNumber() + 1);
            invCountHeaderUpdateList.add(invCountHeaderNew);

            InvCountHeader invCountHeaderDb = invCountHeaderMap.get(invCountHeader.getCountHeaderId());
//          change string materialIds into list
            String materialIds = invCountHeaderDb.getSnapshotMaterialIds();
            List<Long> materialList = Arrays.stream(materialIds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invCountHeader.setMaterialList(materialList);

//          change batchIds into list
            String batchIds = invCountHeaderDb.getSnapshotBatchIds();
            List<Long> batchIdList = Arrays.stream(batchIds.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invCountHeader.setBatchIdList(batchIdList);

//          set departmentId from db
            invCountHeader.setDepartmentId(invCountHeaderDb.getDepartmentId());

            List<InvStockDTO> invStockList = new ArrayList<>();
            if ("SKU".equals(invCountHeader.getCountDimension())) {
                invStockList = invStockRepository.executeBySKU(invCountHeader);
            }

            if ("LOT".equals(invCountHeader.getCountDimension())) {
                invStockList = invStockRepository.executeByLOT(invCountHeader);
            }

            int i = 1;
            for(InvStockDTO invStock : invStockList) {
                InvCountLine invCountLine = new InvCountLine();
                invCountLine.setCountHeaderId(invCountHeader.getCountHeaderId());
                invCountLine.setTenantId(invCountHeader.getTenantId());
                invCountLine.setLineNumber(i);
                invCountLine.setWarehouseId(invStock.getWarehouseId());
                invCountLine.setMaterialId(invStock.getMaterialId());
                invCountLine.setBatchId(invStock.getBatchId());
                invCountLine.setUnitCode(invStock.getUnitCode());
                invCountLine.setSnapshotUnitQty(invStock.getSnapshotUnitQty());
                invCountLine.setCounterIds(invCountHeader.getCounterIds());

                invCountLineInsertList.add(invCountLine);
                i++;
            }
        }
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaderUpdateList);
        invCountLineRepository.batchInsertSelective(invCountLineInsertList);

        return invCountHeaders;
    }

    public InvCountInfoDTO countingOrderSaveVerification (List<InvCountHeaderDTO> invCountHeadersDTO) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<String> errorMessage = new ArrayList<>();
        List<String> statusValidation1 = new ArrayList<>();
        statusValidation1.add("DRAFT");
        statusValidation1.add("INCOUNTING");
        statusValidation1.add("REJECTED");
        statusValidation1.add("WITHDRAWN");

        List<String> statusValidation2 = new ArrayList<>();
        statusValidation2.add("INCOUNTING");
        statusValidation2.add("REJECTED");
        statusValidation2.add("WITHDRAWN");

        List<InvCountHeaderDTO> updateList = invCountHeadersDTO.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

//      this is validation for update
        if(updateList.size() > 0) {
            //  count header
            List<Long> headerIds = updateList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
            String joinHeaderId = String.join(",", headerIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList()));

            Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                    .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

            //  warehouse
            Set<Long> warehouseIds = invCountHeaderMap.values().stream()
                    .map(InvCountHeader::getWarehouseId)
                    .collect(Collectors.toSet());
            ArrayList<Long> warehouseIdList = new ArrayList<>(warehouseIds);

            String joinWarehouseId = String.join(",", warehouseIdList.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList()));

            Map<Long, InvWarehouse> invCountWarehouseMap = invWarehouseRepository.selectByIds(joinWarehouseId).stream()
                    .collect(Collectors.toMap(InvWarehouse::getWarehouseId, header -> header));

            for(InvCountHeaderDTO update : updateList) {
                InvCountHeader invCountHeader = invCountHeaderMap.get(update.getCountHeaderId());

                if(!statusValidation1.contains(String.valueOf(invCountHeader.getCountStatus()))) {
                    errorMessage.add("error1");
                }

                if ("DRAFT".equals(invCountHeader.getCountStatus()) && !String.valueOf(userId).equals(String.valueOf(invCountHeader.getCreatedBy()))) {
                    errorMessage.add("error2");
                }

                if(!statusValidation2.contains(String.valueOf(invCountHeader.getCountStatus()))) {
                    InvWarehouse invWarehouse = invCountWarehouseMap.get(invCountHeader.getWarehouseId());
                    if(invWarehouse == null) {
                        invWarehouse.setIsWmsWarehouse(0);
                    }

                    if (!update.getSupervisorIds().equals(userId.toString()) && invWarehouse.getIsWmsWarehouse() != 0) {
                        errorMessage.add("error3");
                    }
                }

                List<String> listOperator = new ArrayList<>();
                listOperator.add(invCountHeader.getCounterIds());
                listOperator.add(invCountHeader.getSupervisorIds());
                listOperator.add(invCountHeader.getCreatedBy().toString());

                if(!listOperator.contains(String.valueOf(userId))) {
                    errorMessage.add("error4");
                }
            }

            Set<String> setErrorMessage = new HashSet<>();
            if(errorMessage.contains("error1")) {
                setErrorMessage.add("only draft, in counting, rejected, and withdrawn status can be modified.");
            }
            if(errorMessage.contains("error2")) {
                setErrorMessage.add("Document in draft status can only be modified by the document creator.");
            }
            if(errorMessage.contains("error3")) {
                setErrorMessage.add("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
            }
            if(errorMessage.contains("error4")) {
                setErrorMessage.add("only the document creator, counter, and supervisor can modify the document for the status of in counting, rejected, withdrawn.");
            }

            List<String> errorMessageList = new ArrayList<>(setErrorMessage);
            invCountInfoDTO.setErrorMessage(errorMessageList);
        }
        return invCountInfoDTO;
    }

    private boolean isUserInCounterIds(Long userId, String counterIds) {
        // Menggunakan contains dengan format ",userId,"
        return counterIds.contains("," + userId + ",") || counterIds.startsWith(userId + ",") || counterIds.endsWith("," + userId) || counterIds.equals(userId);
    }

    @Override
    public List<InvCountHeaderDTO> countingOrderSave(List<InvCountHeaderDTO> invCountHeadersDTO) {
////      validation data
//        InvCountInfoDTO invCountInfoDTO = countingOrderSaveVerification(invCountHeadersDTO);
//
//        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
//            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
//        }

//      update
        List<InvCountHeaderDTO> updateList = invCountHeadersDTO.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        if(updateList.size() > 0) {
            List<Long> headerIds = updateList.stream().map(InvCountHeader::getCountHeaderId).collect(Collectors.toList());
            String joinHeaderId = String.join(",", headerIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList()));
            Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                    .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

            List<InvCountHeader> updateSpecialCondition = new ArrayList<>();
            List<InvCountHeader> allowedUpdate = new ArrayList<>();
            List<InvCountLine> invCountLineUpdate = new ArrayList<>();

            JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
            Long userId = jsonObject.getLong("id");

            for(InvCountHeaderDTO update : updateList) {
                InvCountHeader invCountHeaderNew = new InvCountHeader();
                invCountHeaderNew.setCountHeaderId(update.getCountHeaderId());
                invCountHeaderNew.setObjectVersionNumber(update.getObjectVersionNumber());

                InvCountHeader countHeaderDb = invCountHeaderMap.get(update.getCountHeaderId());
                if(!"DRAFT".equals(countHeaderDb.getCountStatus()) && !"INCOUNTING".equals(countHeaderDb.getCountStatus())) {
                    update.setRemark(countHeaderDb.getRemark());
                }
                if(!"INCOUNTING".equals(countHeaderDb.getCountStatus()) && !"REJECTED".equals(countHeaderDb.getCountStatus())) {
                    update.setReason(countHeaderDb.getReason());
                }
                if("DRAFT".equals(countHeaderDb.getCountStatus())) {
                    allowedUpdate.add(update);
                } else if ("INCOUNTING".equals(countHeaderDb.getCountStatus())) {
                    invCountHeaderNew.setRemark(update.getRemark());
                    invCountHeaderNew.setReason(update.getReason());
                    updateSpecialCondition.add(invCountHeaderNew);
                } else if ("REJECTED".equals(countHeaderDb.getCountStatus())) {
                    invCountHeaderNew.setReason(update.getReason());
                    updateSpecialCondition.add(invCountHeaderNew);
                }
            }

            if(updateSpecialCondition.size() > 0) {
                invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateSpecialCondition);
            } else {
                invCountHeaderRepository.batchUpdateOptional(allowedUpdate, "companyId", "departmentId", "warehouseId", "countDimension", "countType", "countMode", "countTimeStr", "counterIds", "supervisorIds", "snapshotMaterialIds", "snapshotBatchIds", "remark", "reason", "delFlag");
            }
        }

//      insert
        List<InvCountHeaderDTO> insertList = invCountHeadersDTO.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        if(insertList.size() > 0) {
            List<String> batchCode = codeRuleBuilder.generateCode(insertList.size(), InvCountHeaderConstant.INVOICE_COUNTING, null);

            List<InvCountHeader> invCountHeaderInsertList = new ArrayList<>();
            for (int i = 0; i < insertList.size(); i++) {
                InvCountHeader insert = insertList.get(i);
                insert.setCountNumber(batchCode.get(i));
                insert.setCountStatus("DRAFT");
                insert.setDelFlag(BaseConstants.Flag.NO);

                invCountHeaderInsertList.add(insert);
            }
            invCountHeaderRepository.batchInsertSelective(invCountHeaderInsertList);
        }
        return invCountHeadersDTO;
    }
}