package com.hand.demo.app.service.impl;

import com.hand.demo.api.controller.v1.InvCountHeaderController;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.boot.workflow.dto.RunInstance;
import org.hzero.boot.workflow.dto.RunTaskHistory;
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
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.text.html.Option;
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

    @Autowired
    ProfileClient profileClient;

    @Autowired
    WorkflowClient workflowClient;

    @Autowired
    private TenantRepository tenantRepository;

    private static final Logger log = LoggerFactory.getLogger(InvCountHeaderServiceImpl.class);

//  method for validate missing line, from request and database
    public List<Long> validateLineIdByRequest(InvCountHeaderDTO invCountHeaderDTO) {
//      get line id from request
        List<InvCountLineDTO> countOrderLineList = invCountHeaderDTO.getCountOrderLineList();
        List<Long> lineIdRequest = countOrderLineList.stream()
                .map(InvCountLineDTO::getCountLineId)
                .collect(Collectors.toList());

//      get line id from database
        InvCountLine invCountLine = new InvCountLine();
        invCountLine.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
        List<InvCountLine> invCountLineDb = invCountLineRepository.select(invCountLine);

        List<Long> lineIdDb = invCountLineDb.stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toList());

//      validate line id and line from database
        List<Long> missingLineIds = lineIdRequest.stream()
                .filter(lineId -> !lineIdDb.contains(lineId))
                .collect(Collectors.toList());

//      return missing line if request is missing or not contains from database
        return missingLineIds;
    }

//  method for mapping db
//  map Count Header Db
    public Map<Long, InvCountHeader> mapCountHeaderDb(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        List<Long> headerIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

        return invCountHeaderMap;
    }

    public Map<Long, IamDepartment> mapCountDepartmentDb(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        List<Long> headerIds = invCountHeaderDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        List<InvCountHeader> invCountHeaderListDb = invCountHeaderRepository.selectByIds(joinHeaderId);
        List<Long> departmentIds = invCountHeaderListDb.stream()
                .map(InvCountHeader::getDepartmentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String joinDepartmentId = String.join(",", departmentIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, IamDepartment> countDepartmentMap = iamDepartmentRepository.selectByIds(joinDepartmentId).stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, header -> header));

        return countDepartmentMap;
    }

//  method map line by header Id
    public Map<Long, List<InvCountLine>> mapLineByHeaderIdList(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        List<Long> headerIds = invCountHeaderDTOList.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .collect(Collectors.toList());

        return headerIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            InvCountLine query = new InvCountLine();
                            query.setCountHeaderId(id);
                            return invCountLineRepository.select(query);
                        }
                ));
    }

    //  method map count line db
    public Map<Long, InvCountLine> mapCountLineDb(List<InvCountLineDTO> invCountLineDTOList) {
        List<Long> lineIds = invCountLineDTOList.stream().map(InvCountLineDTO::getCountLineId).collect(Collectors.toList());
        String joinLineIds = String.join(",", lineIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountLine> invCountLineMap = invCountLineRepository.selectByIds(joinLineIds).stream()
                .collect(Collectors.toMap(InvCountLine::getCountLineId, header -> header));
        return invCountLineMap;
    }

//  map Count Line Db

    @Override
    public Page<List<InvCountHeaderDTO>> list(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO) {
        InvCountHeader invCountHeader = new InvCountHeader();
        BeanUtils.copyProperties(invCountHeaderDTO, invCountHeader);

        Page<InvCountHeader> pageResult = PageHelper.doPageAndSort(pageRequest, () -> {
            invCountHeader.setSupervisorIds(invCountHeaderDTO.getSupervisorId());
            return invCountHeaderRepository.selectList(invCountHeader);
        });

        List<Long> headerIds = pageResult.getContent().stream()
                .map(InvCountHeader::getCountHeaderId)
                .collect(Collectors.toList());

        Map<Long, List<InvCountLineDTO>> headerIdLineMap = headerIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            InvCountLine query = new InvCountLine();
                            query.setCountHeaderId(id);
                            List<InvCountLine> invCountLines = invCountLineRepository.select(query);

                            return invCountLines.stream()
                                    .map(line -> {
                                        InvCountLineDTO dto = new InvCountLineDTO();
                                        BeanUtils.copyProperties(line, dto);
                                        return dto;
                                    })
                                    .collect(Collectors.toList());
                        }
                ));

        List<InvCountHeaderDTO> invCountHeaderDTOList = new ArrayList<>();
        for (InvCountHeader entity : pageResult) {
            InvCountHeaderDTO dto = new InvCountHeaderDTO();
            BeanUtils.copyProperties(entity, dto);

            List<InvCountLineDTO> listLine = headerIdLineMap.get(dto.getCountHeaderId());
            if(listLine.size() > 0) {
                dto.setCountOrderLineList(listLine);
            }

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

    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaders) {
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
    public InvCountHeaderDTO detail(Long countHeaderId) {
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

        String materialCodes = invMaterialMap.stream()
                .map(map -> map.get("code").toString())
                .collect(Collectors.joining(","));

        String batchCodes = invBatchMap.stream()
                .map(map -> map.get("code").toString())
                .collect(Collectors.joining(","));

        Long warehouseId = invCountHeader.getWarehouseId();
        InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(warehouseId);

        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);
        invCountHeaderDTO.setCounterList(counterListMap);
        invCountHeaderDTO.setSupervisorList(supervisorListMap);
        invCountHeaderDTO.setSnapshotMaterialList(invMaterialMap);
        invCountHeaderDTO.setSnapshotBatchList(invBatchMap);
        invCountHeaderDTO.setIsWMSwarehouse(invWarehouse.getIsWmsWarehouse());
        invCountHeaderDTO.setWarehouseCode(invWarehouse.getWarehouseCode());
        invCountHeaderDTO.setMaterialCodes(materialCodes);
        invCountHeaderDTO.setBatchCodes(batchCodes);

        InvCountLine invCountLineNew = new InvCountLine();
        invCountLineNew.setCountHeaderId(countHeaderId);
        List<InvCountLine> invCountLineList = invCountLineRepository.select(invCountLineNew);

        if(invCountLineList.size() > 0 && invCountLineList != null) {
            // Map material and batch
            Set<Long> materialIds = invCountLineList.stream()
                    .map(InvCountLine::getMaterialId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<Long> batchIds = invCountLineList.stream()
                    .map(InvCountLine::getBatchId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // change to string
            String materialIdsString = invCountLineList.stream()
                    .map(InvCountLine::getMaterialId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            String batchIdsString = invCountLineList.stream()
                    .map(InvCountLine::getBatchId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            // Fetch data material and batch
            Map<Long, InvMaterial> invCountMaterial = invMaterialRepository.selectByIds(materialIdsString).stream()
                    .collect(Collectors.toMap(
                            material -> material.getMaterialId(),
                            material -> material
                    ));

            Map<Long, InvBatch> invCountBatch = invBatchRepository.selectByIds(batchIdsString).stream()
                    .collect(Collectors.toMap(
                            batch -> batch.getBatchId(),
                            batch -> batch
                    ));

            // InvCountLine into DTO
            List<InvCountLineDTO> invCountLineDTOList = invCountLineList.stream()
                    .map(line -> {
                        InvCountLineDTO dto = new InvCountLineDTO();
                        BeanUtils.copyProperties(line, dto);

                        // Set the material code, name and batch code
                        dto.setMaterialCode(Optional.ofNullable(invCountMaterial.get(line.getMaterialId()))
                                .map(InvMaterial::getMaterialCode)
                                .orElse(null));
                        dto.setMaterialName(Optional.ofNullable(invCountMaterial.get(line.getMaterialId()))
                                .map(InvMaterial::getMaterialName)
                                .orElse(null));
                        dto.setBatchCode(Optional.ofNullable(invCountBatch.get(line.getBatchId()))
                                .map(InvBatch::getBatchCode)
                                .orElse(null));

                        // Format snapshotUnitQty, unitQty, and unitDiffQty to 2 decimal places
                        if (line.getSnapshotUnitQty() != null) {
                            dto.setSnapshotUnitQty(new BigDecimal(line.getSnapshotUnitQty().toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
                        }
                        if (line.getUnitQty() != null) {
                            dto.setUnitQty(new BigDecimal(line.getUnitQty().toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
                        }
                        if (line.getUnitDiffQty() != null) {
                            dto.setUnitDiffQty(new BigDecimal(line.getUnitDiffQty().toString()).setScale(2, BigDecimal.ROUND_HALF_UP));
                        }

                        return dto;
                    })
                    .collect(Collectors.toList());

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
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        List<InvCountInfoDTO> resultList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();

        List<InvCountExtra> invCountExtraListInsert = new ArrayList<>();
        List<InvCountExtra> invCountExtraListUpdate = new ArrayList<>();
        List<InvCountHeader> invCountHeaderListUpdate = new ArrayList<>();

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        Map<Long, InvCountHeader> invCountHeaderMap = mapCountHeaderDb(invCountHeaderDTOList);
        // Process each header
        for (InvCountHeaderDTO invCountHeader : invCountHeaderDTOList) {
            InvCountHeader invCountHeaderDb = invCountHeaderMap.get(invCountHeader.getCountHeaderId());

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
                invCountHeader.setCountStatus(invCountHeaderDb.getCountStatus());

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
                    syncStatusExtra.setProgramvalue("S");
                    syncMsgExtra.setProgramvalue("");
                    invCountHeaderNew.setRelatedWmsOrderCode(response.getString("code"));
                    invCountHeaderNew.setObjectVersionNumber(invCountHeaderDb.getObjectVersionNumber());
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

    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO) {
        InvCountHeader invCountHeaderDb = invCountHeaderRepository.selectByPrimary(invCountHeaderDTO.getCountHeaderId());

        JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

        //      map invCountLine
        Map<Long, InvCountLine> invCountLineMap = mapCountLineDb(invCountHeaderDTO.getCountOrderLineList());
        List<InvCountLineDTO> invCountLineList = invCountHeaderDTO.getCountOrderLineList();

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

        List<InvCountLine> invCountLineUpdateList = new ArrayList<>();
        for(InvCountLine invCountLines : invCountLineList) {
            InvCountLine invCountLineNew = new InvCountLine();
            invCountLineNew.setCountLineId(invCountLines.getCountLineId());
            invCountLineNew.setRemark(invCountLines.getRemark());
            invCountLineNew.setObjectVersionNumber(invCountLines.getObjectVersionNumber());

            InvCountLine invCountLineData = invCountLineMap.get(invCountLines.getCountLineId());
            BigDecimal unitQtyDecimal = BigDecimal.valueOf(((Number) invCountLines.getUnitQty()).doubleValue());
            BigDecimal snapshotUnitQtyDecimal = BigDecimal.valueOf(((Number) invCountLineData.getSnapshotUnitQty()).doubleValue());
            BigDecimal unitDiffQty = unitQtyDecimal.subtract(snapshotUnitQtyDecimal).setScale(2, BigDecimal.ROUND_HALF_UP);
            invCountLineNew.setCountHeaderId(invCountLineData.getCountHeaderId());
            invCountLineNew.setUnitDiffQty(unitDiffQty);

            invCountLineUpdateList.add(invCountLineNew);
        }

        invCountLineRepository.batchUpdateByPrimaryKeySelective(invCountLineUpdateList);
        return invCountHeaderDTO;
    }

    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

        Map<Long, InvCountHeader> invCountHeaderMap = mapCountHeaderDb(invCountHeaderDTOList);
        Map<Long, List<InvCountLine>> invCountLineMapByHeaderId = mapLineByHeaderIdList(invCountHeaderDTOList);

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

            List<String> supervisorIdList = Arrays.asList(invCountHeaderDb.getSupervisorIds().split(","));
            if (!supervisorIdList.contains(userId.toString())) {
                errorMsg.add("error2");
            }

            boolean hasNullOrEmptyUnitQty = invCountLineDb.stream()
                    .anyMatch(line -> line.getUnitQty() == null || line.getUnitQty().toString().trim().isEmpty());

            if (hasNullOrEmptyUnitQty) {
                errorMsg.add("error3");
            }

            List<InvCountLine> invCountLines = invCountLineDb;
            List<String> errorMessages = invCountLines.stream()
                    .filter(invCountLine -> invCountLine.getUnitDiffQty() == null)
                    .map(invCountLine -> "error4")
                    .collect(Collectors.toList());

            if (!errorMessages.isEmpty()) {
                if(invCountHeaderDb.getReason() == null) {
                    errorMsg.add("error4");
                }
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

    @Override
    public InvCountHeaderDTO countingOrderCallBack(WorkFlowEventDTO workFlowEventRequestDTO) {
        String businessKey = workFlowEventRequestDTO.getBusinessKey();
        String docStatus = workFlowEventRequestDTO.getDocStatus();
        Long workflowId = workFlowEventRequestDTO.getWorkflowId();
        Date approvedTime = workFlowEventRequestDTO.getApprovedTime();

        InvCountHeader invCountHeader = new InvCountHeader();
        invCountHeader.setCountNumber(businessKey);

        InvCountHeader header = invCountHeaderRepository.selectOne(invCountHeader);

        header.setCountStatus(docStatus);
        header.setWorkflowId(workflowId);
        header.setApprovedTime(approvedTime);

        invCountHeaderRepository.updateByPrimaryKeySelective(header);

        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, invCountHeaderDTO);
        return invCountHeaderDTO;
    }

    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderDTOList) {
        //  countingOrderSaveVerification
        InvCountInfoDTO invCountInfoDTO = manualSaveCheck(invCountHeaderDTOList);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        //  Counting order save
        manualSave(invCountHeaderDTOList);

        //  countingOrderSubmitVerification
        InvCountInfoDTO invCountInfoDTOSubmit = submitCheck(invCountHeaderDTOList);
        if(invCountInfoDTOSubmit.getErrorMessage() != null && invCountInfoDTOSubmit.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTOSubmit));
        }

        Map<Long, InvCountHeader> mapCountHeaderDb = mapCountHeaderDb(invCountHeaderDTOList);
        Map<Long, IamDepartment> mapCountDepartmentDb = mapCountDepartmentDb(invCountHeaderDTOList);

        List<InvCountHeader> invCountHeaderListUpdate = new ArrayList<>();

        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOList) {
            InvCountHeader invCountHeaderDb = mapCountHeaderDb.get(invCountHeaderDTO.getCountHeaderId());
            IamDepartment iamDepartmentDb = mapCountDepartmentDb.get(invCountHeaderDb.getDepartmentId());

            String workflowFlag = profileClient.getProfileValueByOptions(invCountHeaderDTO.getTenantId(), null, null, "FEXAM56.INV.COUNTING.ISWORKFLO");
            Long organizationId = invCountHeaderDb.getTenantId();
            String flowKey = InvCountHeaderConstant.FLOW_KEY;
            String businessKey = invCountHeaderDb.getCountNumber();
            String dimension = "EMPLOYEE";
            String starter = "47356";
            Map<String, Object> map = new HashMap<>();
            map.put("departmentCode", iamDepartmentDb.getDepartmentCode());

            if(workflowFlag != null) {
                RunInstance response = workflowClient.startInstanceByFlowKey(
                        organizationId,
                        flowKey,
                        businessKey,
                        dimension,
                        starter,
                        map);
            } else {
                invCountHeaderDb.setCountStatus("CONFIRMED");
                invCountHeaderListUpdate.add(invCountHeaderDb);
            }
        }

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaderListUpdate);
        return invCountHeaderDTOList;
    }

    @ProcessCacheValue
    public List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTO) {
        List<InvCountHeaderDTO> invCountHeaderDTOListResult = new ArrayList<>();
        InvCountHeaderDTO invCountHeaderDTOAdd = detail(invCountHeaderDTO.getCountHeaderId());

        Tenant tenant = tenantRepository.selectByPrimary(invCountHeaderDTO.getTenantId());
        invCountHeaderDTOAdd.setTenantName(tenant.getTenantName());

        IamDepartment iamDepartment = iamDepartmentRepository.selectByPrimary(invCountHeaderDTOAdd.getDepartmentId());
        invCountHeaderDTOAdd.setDepartmentName(iamDepartment.getDepartmentName());

        invCountHeaderDTOAdd.setCreatorId(invCountHeaderDTOAdd.getCreatedBy());

        List<RunTaskHistory> runTaskHistoryList = workflowClient.approveHistoryByFlowKey(invCountHeaderDTOAdd.getTenantId(), InvCountHeaderConstant.FLOW_KEY, invCountHeaderDTOAdd.getCountNumber());
        invCountHeaderDTOAdd.setRunHistoryList(runTaskHistoryList);
        invCountHeaderDTOListResult.add(invCountHeaderDTOAdd);
        return invCountHeaderDTOListResult;
    }

    public List<InvStockDTO> filterList(List<InvStockDTO> invStockList, InvCountHeaderDTO invCountHeader) {
        return invStockList.stream()
                .filter(invStock -> invStock.getTenantId() == invCountHeader.getTenantId())
                .filter(invStock -> invStock.getCompanyId() == invCountHeader.getCompanyId())
                .filter(invStock -> invStock.getDepartmentId() == invCountHeader.getDepartmentId())
                .filter(invStock -> invStock.getWarehouseId() == invCountHeader.getWarehouseId())
                .filter(invStock -> invCountHeader.getMaterialList().contains(invStock.getMaterialId()))
                .filter(invStock ->
                        invCountHeader.getBatchIdList() == null ||
                                invCountHeader.getBatchIdList().contains(invStock.getBatchId())
                )
                .collect(Collectors.toList());
    }

    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOList) {
    // create invCountHeader map db by id
        Map<Long, InvCountHeader> invCountHeaderMap = mapCountHeaderDb(invCountHeaderDTOList);
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

        //  set material list on invCountHeaderDTO
        invCountHeaderDTOList.forEach(invCountHeaderDTO -> {
            List<Long> materialList = Arrays.stream(invCountHeaderDTO.getSnapshotMaterialIds().split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invCountHeaderDTO.setMaterialList(materialList);
        });

        List<InvStockDTO> invStockList = invStockRepository.checkOnHandQuantity(invCountHeaderDTOList)
                .stream()
                .distinct()
                .collect(Collectors.toList());

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
            invCountHeaderDTO.setDepartmentId(invCountHeader.getDepartmentId());

            List<InvStockDTO> filteredList = filterList(invStockList, invCountHeaderDTO);

            if (filteredList.isEmpty()) {
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

    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaders) {
        //      countingOrderSaveVerification
        InvCountInfoDTO invCountInfoDTO = manualSaveCheck(invCountHeaders);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        //      Counting order save
        manualSave(invCountHeaders);

        //      Counting order execute verification
        InvCountInfoDTO invCountInfoDTOExecute = executeCheck(invCountHeaders);
        if(invCountInfoDTOExecute.getErrorMessage() != null && invCountInfoDTOExecute.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTOExecute));
        }

        //      Execute
        // create invCountHeader map db by id
        List<Long> headerIds = invCountHeaders.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));
        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

        List<InvCountHeader> invCountHeaderUpdateList = new ArrayList<>();
        List<InvCountLine> invCountLineInsertList = new ArrayList<>();

        //  convert material and batch from string to list
        invCountHeaders.forEach(invCountHeaderDTO -> {
            List<Long> materialList = Arrays.stream(invCountHeaderDTO.getSnapshotMaterialIds().split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invCountHeaderDTO.setMaterialList(materialList);

            if(invCountHeaderDTO.getSnapshotBatchIds() != null) {
                List<Long> batchList = Arrays.stream(invCountHeaderDTO.getSnapshotBatchIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                invCountHeaderDTO.setBatchIdList(batchList);
            }
        });

        List<InvStockDTO> invStockList = invStockRepository.execute(invCountHeaders)
                .stream()
                .distinct()
                .collect(Collectors.toList());
        System.out.println(invStockList);

        for(InvCountHeaderDTO invCountHeader : invCountHeaders) {
            InvCountHeader invCountHeaderDb = invCountHeaderMap.get(invCountHeader.getCountHeaderId());
            invCountHeaderDb.setCountStatus("INCOUNTING");
            invCountHeaderUpdateList.add(invCountHeaderDb);

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

            List<InvStockDTO> filteredList = filterList(invStockList, invCountHeader);

            int i = 1;
            for(InvStockDTO invStock : filteredList) {
                InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
                invCountLineDTO.setCountHeaderId(invCountHeader.getCountHeaderId());
                invCountLineDTO.setTenantId(invCountHeader.getTenantId());
                invCountLineDTO.setLineNumber(i);
                invCountLineDTO.setWarehouseId(invStock.getWarehouseId());
                invCountLineDTO.setMaterialId(invStock.getMaterialId());
                invCountLineDTO.setBatchId(invStock.getBatchId());
                invCountLineDTO.setUnitCode(invStock.getUnitCode());
                invCountLineDTO.setSnapshotUnitQty(invStock.getSnapshotUnitQty());
                invCountLineDTO.setCounterIds(invCountHeader.getCounterIds());
                invCountLineDTO.setUnitCode(invStock.getUnitCode());

                invCountLineInsertList.add(invCountLineDTO);
                i++;
            }
        }

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaderUpdateList);
        invCountLineRepository.batchInsertSelective(invCountLineInsertList);

        //      Counting order synchronization
        countSyncWms(invCountHeaders);
        return invCountHeaders;
    }

    public InvCountInfoDTO manualSaveCheck (List<InvCountHeaderDTO> invCountHeadersDTO) {
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

                    if (Arrays.stream(update.getSupervisorIds().split(","))
                            .noneMatch(id -> id.trim().equals(userId.toString()))
                            && invWarehouse.getIsWmsWarehouse() != 0) {
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

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeadersDTO) {
        //      countingOrderSaveVerification
        InvCountInfoDTO invCountInfoDTO = manualSaveCheck(invCountHeadersDTO);
        if(invCountInfoDTO.getErrorMessage() != null && invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

        //      set error message into null, if there is no error message
        invCountInfoDTO.setErrorMessage(null);

        //      update
        List<InvCountLine> invCountLineDTOUpdateList = new ArrayList<>();
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

            String countLineIds = invCountHeadersDTO.stream()
                    .flatMap(headerDTO -> headerDTO.getCountOrderLineList().stream())
                    .map(InvCountLineDTO::getCountLineId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            List<InvCountLine> invCountLineList = invCountLineRepository.selectByIds(countLineIds);
            Map<Long, InvCountLine> mapLineDb = invCountLineList.stream()
                    .collect(Collectors.toMap(InvCountLine::getCountLineId, line -> line));

            for(InvCountHeaderDTO update : updateList) {
//              update header
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

                if(countHeaderDb.getCountStatus().equals("INCOUNTING")) {
                    List<InvCountLineDTO> invCountLineDTOList = update.getCountOrderLineList();
                    for(InvCountLine invCountLineDTO : invCountLineDTOList) {
                        InvCountLine invCountLineDb = mapLineDb.get(invCountLineDTO.getCountLineId());

                        String counterIds = invCountLineDb.getCounterIds();
                        if (counterIds.contains(String.valueOf(userId))) {
                            invCountLineDTO.setCounterIds(userId.toString());
                            invCountLineDTOUpdateList.add(invCountLineDTO);
                        }
                    }
                }
            }

            if(updateSpecialCondition.size() > 0) {
                invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateSpecialCondition);
            } else {
                invCountHeaderRepository.batchUpdateOptional(allowedUpdate, "companyId", "departmentId", "warehouseId", "countDimension", "countType", "countMode", "countTimeStr", "counterIds", "supervisorIds", "snapshotMaterialIds", "snapshotBatchIds", "remark", "reason", "delFlag");
            }

            if(invCountLineDTOUpdateList.size() > 0) {
                invCountLineRepository.batchUpdateByPrimaryKeySelective(invCountLineDTOUpdateList);
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