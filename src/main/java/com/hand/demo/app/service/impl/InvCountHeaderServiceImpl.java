package com.hand.demo.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountHeaderConstant;
import com.hand.demo.infra.mapper.InvStockMapper;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.cache.Cacheable;
import org.hzero.core.cache.ProcessCacheValue;
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 08:22:37
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private LovAdapter lovAdapter;

    @Autowired
    private IamRemoteService iamRemoteService;

    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Autowired
    private CodeRuleBuilder codeRuleBuilder;

    @Autowired
    private InvCountLineService invCountLineService;

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
    private InvStockMapper invStockMapper;

    @Autowired
    private InvCountExtraRepository invCountExtraRepository;

    @Autowired
    private InterfaceInvokeSdk interfaceInvokeSdk;


    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public void saveData(List<InvCountHeaderDTO> invCountHeaders) {
        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<LovValueDTO> validStatusList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_STATUS,
                BaseConstants.DEFAULT_TENANT_ID);
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);


        List<String> validStatuses = validStatusList.stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
        List<Long> warehouseWMSIds  = invWarehouseRepository.selectList(new InvWarehouse().setIsWmsWarehouse(1)).stream()
                .map(InvWarehouse::getWarehouseId).collect(Collectors.toList());

        StringBuilder errorMessages = new StringBuilder();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        for (InvCountHeaderDTO data : invCountHeaderDTOS) {
            StringBuilder lineError = new StringBuilder();
            InvCountHeader header = invCountHeaderRepository.selectOne(data);

            if (data.getCountHeaderId() != null) {
                if (!validStatuses.contains(data.getCountStatus())) {
                    data.setErrMsg(String.valueOf(lineError.append("Data status allowed: only draft, in counting," +
                            " rejected, and withdrawn status can be modified (").append(data.getCountHeaderId()).append("), ")));
                }

                if (Objects.equals(data.getCountStatus(), "DRAFT") &&  data.getCreatedBy() != jsonObject.getLong("id")) {
                    data.setErrMsg(String.valueOf(lineError.append("Document in draft status can only be modified " +
                            "by the document creator. (").append(data.getCountHeaderId()).append("), ")));
                }

                if (!Objects.equals(data.getCountStatus(), "DRAFT")) {
                    if (warehouseWMSIds.contains(data.getWarehouseId()) && !Arrays.asList(data.getSupervisorIds().split(","))
                            .contains(String.valueOf(jsonObject.getLong("id")))) {
                        data.setErrMsg(String.valueOf(
                                lineError.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate. (")
                                        .append(data.getCountHeaderId()).append("), ")
                        ));
                    }

                    if (!Arrays.asList(data.getSupervisorIds().split(","))
                            .contains(String.valueOf(jsonObject.getLong("id"))) ||
                            !Arrays.asList(data.getCounterIds().split(","))
                            .contains(String.valueOf(jsonObject.getLong("id"))) ||
                            jsonObject.getLong("id") != data.getCreatedBy()
                    ) {
                        data.setErrMsg(
                                String.valueOf(lineError.append("Only the document creator, counter, and supervisor can " +
                                        "modify the document for the status of in counting, rejected, withdrawn.")
                                        .append(data.getCountHeaderId()).append("), "))
                        );
                    }
                }


                if (lineError.length() > 0) {
                    errorMessages.append("Header Data: ").append(data.getCountHeaderId()).append(", ")
                            .append(lineError).append("\n");
                }
            }

        }

        invCountInfoDTO.setListErrMsg(
                invCountHeaderDTOS.stream()
                        .filter(header -> header.getErrMsg() != null)
                        .collect(Collectors.toList())
        );

        invCountInfoDTO.setErrMsg(errorMessages.toString());

        if (errorMessages.length() == 0 ) {
            invCountInfoDTO.setSuccessMsg("Success");
        }

        return invCountInfoDTO;
    }

    @Transactional
    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        Map<String, String> variableMap = new HashMap<>();
        Map<Long, List<InvCountLine>> stringListMap = new HashMap<>();
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);
        variableMap.put("customSegment", String.valueOf(jsonObject.getLong("tenantId")));
        List<InvCountLine> lines = new ArrayList<>();

        invCountHeaderDTOS.forEach(header -> {
            if (header.getCountNumber() == null) {
                String headerNumber = codeRuleBuilder.generateCode(InvCountHeaderConstant.RULE_CODE, variableMap);
                header.setCountNumber(headerNumber);
                header.setCountStatus("DRAFT");
//                stringListMap.put(headerNumber, header.getLines());
            } else {
                stringListMap.put(header.getCountHeaderId(), header.getCountOrderLineList());
            }
        });

        List<InvCountHeader> insertList = invCountHeaderDTOS.stream()
                .filter(header -> header.getCountHeaderId() == null)
                .collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaderDTOS.stream()
                .filter(header -> header.getCountHeaderId() != null)
                .collect(Collectors.toList());


        invCountHeaderRepository.batchInsertSelective(insertList);
//        List<InvCountHeader> insertResult = invCountHeaderRepository.batchInsertSelective(insertList);

//        List<InvCountLine> listLines = invCountLineRepository.selectAll();
//        AtomicInteger lineNumber = new AtomicInteger(
//                !listLines.isEmpty() ? listLines.get(listLines.size() - 1).getLineNumber() : 0
//        );

//        insertResult.forEach(header -> {
//            List<InvCountLine> lineMap = stringListMap.get(header.getCountNumber());
//            for (InvCountLine line : lineMap) {
//                line.setCountHeaderId(header.getCountHeaderId());
//                line.setLineNumber(lineNumber.incrementAndGet());
//                lines.add(line);
//            }
//        });

//        List<InvCountHeader> updateResult = invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
        updateState(updateList);
        updateList.forEach(header -> {
            List<InvCountLine> lineMap = stringListMap.get(header.getCountHeaderId());
            lines.addAll(lineMap);
        });

        List<InvCountLineDTO> invCountLineDTOS = new ArrayList<>();

        for (InvCountLine line : lines) {
            InvCountLineDTO dto = new InvCountLineDTO();
            BeanUtils.copyProperties(line, dto);
            invCountLineDTOS.add(dto);
        }
        invCountLineService.saveData(invCountLineDTOS);
        return invCountHeaderDTOS;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        StringBuilder errorMessages = new StringBuilder();
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);

        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {
            StringBuilder lineError = new StringBuilder();
            if (!Objects.equals(invCountHeaderDTO.getCountStatus(), "DRAFT")) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Only allow draft status to be deleted")
                        .append(", ")));
            }
            if (jsonObject.getLong("id") != invCountHeaderDTO.getCreatedBy()) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Only allow draft status to be deleted")
                        .append(", ")));
            }

            if (!invCountHeaderDTO.getErrMsg().isEmpty()) {
                errorMessages.append("Error: ").append(invCountHeaderDTO.getCountHeaderId()).append(", ")
                        .append(lineError).append("\n");
            }
        }

        invCountInfoDTO.setListErrMsg(invCountHeaderDTOS.stream().filter(invCountHeaderDTO ->
                invCountHeaderDTO.getErrMsg() != null).collect(Collectors.toList()));

        invCountInfoDTO.setErrMsg(errorMessages.toString());

        if (errorMessages.length() == 0 ) {
            invCountInfoDTO.setSuccessMsg("Success");
            invCountInfoDTO.setListSuccessMsg(invCountHeaderDTOS);
        }

        return invCountInfoDTO;

    }

    @Override
    public InvCountHeaderDTO detail(Long id) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(id);

        if (invCountHeader == null) {
            throw new CommonException("OrderHeader not found for id: " + id);
        }

        InvCountHeaderDTO dto = new InvCountHeaderDTO();
        BeanUtils.copyProperties(invCountHeader, dto);

        String counterIds = dto.getCounterIds();
        String supervisorIds = (dto.getSupervisorIds() != null) ? dto.getSupervisorIds() : "";
        String materialIds = (dto.getSnapshotMaterialIds() != null) ? dto.getSnapshotMaterialIds() : "";
        String batchIds = (dto.getSnapshotBatchIds() != null) ? dto.getSnapshotBatchIds() : "";


        List<IamDTO> counterList = new ArrayList<>();
        List<IamDTO> supervisorList = new ArrayList<>();
        List<InvMaterialDTO> materialDTOS = new ArrayList<>();
        List<InvBatchDTO> batchDTOS = new ArrayList<>();


        if (counterIds != null && !counterIds.trim().isEmpty()) {
            String[] idsCounter = counterIds.split(",");
            for (String idCounter : idsCounter) {
                Long counterId = Long.valueOf(idCounter);
                IamDTO counter = new IamDTO();
                counter.setId(counterId);
                counterList.add(counter);
            }
        } else {
            counterList = Collections.emptyList();
        }

        if (!supervisorIds.trim().isEmpty()) {
            String[] idsSupervisor = supervisorIds.split(",");
            for (String idSupervisor : idsSupervisor) {
                Long supervisorId = Long.valueOf(idSupervisor);
                IamDTO supervisor = new IamDTO();
                supervisor.setId(supervisorId);
                supervisorList.add(supervisor);
            }
        } else {
            supervisorList = Collections.emptyList();
        }

        List<InvMaterial> materialListData =  invMaterialRepository.selectByIds(materialIds);
        if (!materialListData.isEmpty()) {
            for (InvMaterial invMaterial : materialListData) {
                InvMaterialDTO materialDTO = new InvMaterialDTO();
                materialDTO.setMaterialId(invMaterial.getMaterialId());
                materialDTO.setMaterialCode(invMaterial.getMaterialCode());
                materialDTOS.add(materialDTO);
            }
        }

        List<InvBatch> batchListData =  invBatchRepository.selectByIds(batchIds);
        if (!materialListData.isEmpty()) {
            for (InvBatch invBatch : batchListData) {
                InvBatchDTO batchDTO = new InvBatchDTO();
                batchDTO.setBatchId(invBatch.getBatchId());
                batchDTO.setBatchCode(invBatch.getBatchCode());
                batchDTOS.add(batchDTO);
            }
        }
        int isWMS = invWarehouseRepository.selectOne(new InvWarehouse().setWarehouseId(invCountHeader.getWarehouseId()))
                .getIsWmsWarehouse();

        dto.setCounterList(counterList);
        dto.setSupervisorList(supervisorList);
        dto.setSnapshotMaterialList(materialDTOS);
        dto.setSnapshotBatchList(batchDTOS);
        dto.setWMSwarehouse(isWMS == 1);

        return dto;
    }

    private void updateState(List<InvCountHeader> invCountHeaders) {
        invCountHeaderRepository.batchUpdateOptional(invCountHeaders.stream().filter(data ->
                        Objects.equals(data.getCountStatus(), "INCOUNTING")).collect(Collectors.toList()),
                InvCountHeader.FIELD_REMARK, InvCountHeader.FIELD_REASON);
        invCountHeaderRepository.batchUpdateOptional(invCountHeaders.stream().filter(data ->
                        Objects.equals(data.getCountStatus(), "REJECTED")).collect(Collectors.toList()),
                InvCountHeader.FIELD_REASON);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders.stream().filter(data ->
                Objects.equals(data.getCountStatus(), "DRAFT")).collect(Collectors.toList()));
    }

    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        StringBuilder errorMessages = new StringBuilder();
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);

        List<LovValueDTO> validStatusTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_STATUS,
                BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> validDimensionTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_DIMENSION,
                BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> validCountTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_TYPE,
                BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> validModeTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_MODE,
                BaseConstants.DEFAULT_TENANT_ID);

        List<String> validStatusList = validStatusTypesList.stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
        List<String> validDimensionList = validDimensionTypesList.stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
        List<String> validCountTypeList = validCountTypesList.stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
        List<String> validModeList = validModeTypesList.stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());

        List<Long> listCompanyIds = iamCompanyRepository.selectAll()
                .stream().map(IamCompany::getCompanyId).collect(Collectors.toList());
        List<Long> listDepartmentIds = iamDepartmentRepository.selectAll().stream()
                .map(IamDepartment::getDepartmentId).collect(Collectors.toList());
        List<Long> listWarehouseIds = invWarehouseRepository.selectAll().stream()
                .map(InvWarehouse::getWarehouseId).collect(Collectors.toList());


        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {
            StringBuilder lineError = new StringBuilder();
            List<BigDecimal> quantities = invStockMapper.getQuantities(invCountHeaderDTO);

            if (!Objects.equals(invCountHeaderDTO.getCountStatus(), "DRAFT")) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Only allow draft status to be deleted")
                        .append(", ")));
            }
            if (jsonObject.getLong("id") != invCountHeaderDTO.getCreatedBy()) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Only allow draft status to be deleted")
                        .append(", ")));
            }
            if (!validStatusList.contains(invCountHeaderDTO.getCountStatus())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Status wrong: ")
                        .append(invCountHeaderDTO.getCountStatus())
                        .append(", ")));
            }
            if (!validDimensionList.contains(invCountHeaderDTO.getCountDimension())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Dimension wrong: ")
                        .append(invCountHeaderDTO.getCountDimension())
                        .append(", ")));
            }
            if (!validCountTypeList.contains(invCountHeaderDTO.getCountType())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Count type wrong: ")
                        .append(invCountHeaderDTO.getCountType())
                        .append(", ")));
            }
            if (!validModeList.contains(invCountHeaderDTO.getCountMode())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Mode wrong: ")
                        .append(invCountHeaderDTO.getCountMode())
                        .append(", ")));
            }
            if (!listCompanyIds.contains(invCountHeaderDTO.getCompanyId())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Company Id not found: ")
                        .append(invCountHeaderDTO.getCompanyId())
                        .append(", ")));
            }
            if (!listDepartmentIds.contains(invCountHeaderDTO.getDepartmentId())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Department Id not found: ")
                        .append(invCountHeaderDTO.getDepartmentId())
                        .append(", ")));
            }
            if (!listWarehouseIds.contains(invCountHeaderDTO.getWarehouseId())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Warehouse Id not found: ")
                        .append(invCountHeaderDTO.getWarehouseId())
                        .append(", ")));
            }
            if (quantities == null || quantities.isEmpty()) {
                invCountHeaderDTO.setErrMsg(String.valueOf(
                        lineError.append("Unable to query on hand quantity data: QTY ->")
                                .append(quantities == null ? "null" : "empty")
                                .append(", ")
                ));
            } else if (quantities.stream().anyMatch(q -> q.compareTo(BigDecimal.ZERO) == 0)) {
                invCountHeaderDTO.setErrMsg(String.valueOf(
                        lineError.append("Some queried quantities are zero: QTY ->")
                                .append(quantities)
                                .append(", ")
                ));
            }

            if (lineError.length() > 0) {
                errorMessages.append("Header Data: ").append(invCountHeaderDTO.getCountHeaderId()).append(", ")
                        .append(lineError).append("\n");
            }

        }

        invCountInfoDTO.setListErrMsg(invCountHeaderDTOS.stream().filter(invCountHeaderDTO ->
                invCountHeaderDTO.getErrMsg() != null).collect(Collectors.toList()));

        invCountInfoDTO.setErrMsg(errorMessages.toString());

        if (errorMessages.length() == 0 ) {
            invCountInfoDTO.setSuccessMsg("Success");
            invCountInfoDTO.setListSuccessMsg(invCountHeaderDTOS);
        }

        return invCountInfoDTO;
    }

    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<InvCountLineDTO> lines = new ArrayList<>();

        invCountHeaderDTOS.forEach(header -> {
            header.setCountStatus("INCOUNTING");
        });
        List<InvCountHeader> invCountHeaders = new ArrayList<>(invCountHeaderDTOS);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders);

        List<InvCountLine> listLines = invCountLineRepository.selectAll();
        AtomicInteger lineNumber = new AtomicInteger(
                !listLines.isEmpty() ? listLines.get(listLines.size() - 1).getLineNumber() : 0
        );

        BeanUtils.copyProperties(invCountHeaders, invCountHeaderDTOS);

        invCountHeaderDTOS.forEach(header -> {
//            List<InvCountLine> lineMap = stringListMap.get(header.getCountHeaderId());
//            for (InvCountLine line : lineMap) {
//                line.setCountHeaderId(header.getCountHeaderId());
//                line.setLineNumber(lineNumber.incrementAndGet());
//                line.setCounterIds(header.getCounterIds());
//                lines.add(line);
//            }

            List<InvStock> listStock = invStockMapper.getListForQuantity(header);
            for (InvStock stock : listStock) {
                InvCountLineDTO lineDTO = new InvCountLineDTO();
                lineDTO.setTenantId(header.getTenantId());
                lineDTO.setCountHeaderId(header.getCountHeaderId());
                lineDTO.setLineNumber(lineNumber.incrementAndGet());
                lineDTO.setWarehouseId(stock.getWarehouseId());
                lineDTO.setMaterialId(stock.getMaterialId());
                lineDTO.setUnitCode(stock.getUnitCode());
                lineDTO.setBatchId(stock.getBatchId());
                lineDTO.setSnapshotUnitQty(stock.getUnitQuantity());
                lineDTO.setUnitQty(BigDecimal.ZERO);
                lineDTO.setUnitDiffQty(BigDecimal.ZERO);
                lineDTO.setCounterIds(header.getCounterIds());
                lines.add(lineDTO);
            }
        });

//        List<InvCountLineDTO> invCountLineDTOS = new ArrayList<>();
//        for (InvCountLine line : lines) {
//            InvCountLineDTO dto = new InvCountLineDTO();
//            BeanUtils.copyProperties(line, dto);
//            invCountLineDTOS.add(dto);
//        }
        invCountLineService.saveData(lines);
        return invCountHeaderDTOS;
    }

    @Transactional
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        String headerWarehouseIds = invCountHeaderDTOS.stream().map(InvCountHeaderDTO::getWarehouseId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        List<Long> warehouseWMSIds  = invWarehouseRepository.selectList(new InvWarehouse().setIsWmsWarehouse(1)).stream()
                .map(InvWarehouse::getWarehouseId).collect(Collectors.toList());
        List<Long> warehouseExistIds = invWarehouseRepository.selectByIds(headerWarehouseIds).stream().filter(header ->
                        Objects.equals(header.getTenantId(), BaseConstants.DEFAULT_TENANT_ID))
                .map(InvWarehouse::getWarehouseId)
                .collect(Collectors.toList());
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        StringBuilder errorMessages = new StringBuilder();
        InvCountExtra syncStatusExtra;
        InvCountExtra syncMsgExtra;

        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            StringBuilder lineError = new StringBuilder();
//            boolean isWhEmpty = invWarehouseRepository.selectList(new InvWarehouse()
//                    .setWarehouseId(invCountHeaderDTO.getWarehouseId())
//                    .setTenantId(BaseConstants.DEFAULT_TENANT_ID)).isEmpty();

            if (!warehouseExistIds.contains(invCountHeaderDTO.getWarehouseId())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Warehouse not found")
                        .append(", ")));
            }


            List<InvCountExtra> invCountExtras = invCountExtraRepository.select(new InvCountExtra()
                    .setSourceid(invCountHeaderDTO.getCountHeaderId())
                    .setEnabledflag(1));

            if (invCountExtras.isEmpty()) {
                syncStatusExtra = new InvCountExtra()
                        .setTenantid(invCountHeaderDTO.getTenantId())
                        .setEnabledflag(1)
                        .setProgramkey("wms_sync_status");
                syncMsgExtra = new InvCountExtra()
                        .setTenantid(invCountHeaderDTO.getTenantId())
                        .setEnabledflag(1)
                        .setProgramkey("wms_sync_error_message");
            } else {
                Map<String, InvCountExtra> mapExtraData = invCountExtras.stream()
                        .collect(Collectors.toMap(InvCountExtra::getProgramkey, Function.identity()));

                syncStatusExtra = mapExtraData.get("wms_sync_status");
                syncMsgExtra = mapExtraData.get("wms_sync_error_message");
            }

            if (!warehouseWMSIds.contains(invCountHeaderDTO.getWarehouseId())) {
                syncStatusExtra.setProgramvalue("SKIP");
            }

            invCountHeaderDTO.setEmployeeNumber("21995");
            ResponsePayloadDTO response =  invokeWms(invCountHeaderDTO);


            if (response.getBody() == null) {
                throw new CommonException("Response from external is null");
            }

            JSONObject jsonObject = new JSONObject(response.getBody());

            if (Objects.equals(jsonObject.getString("returnStatus"), "S")) {
                syncStatusExtra.setProgramvalue("SUCCESS");
                syncMsgExtra.setProgramvalue("");
                invCountHeaderDTO.setRelatedWmsOrderCode(jsonObject.getString("CODE"));
                invCountHeaderRepository.updateOptional(invCountHeaderDTO, InvCountHeaderDTO.FIELD_RELATED_WMS_ORDER_CODE);
            } else {
                syncStatusExtra.setProgramvalue("ERROR");
                syncMsgExtra.setProgramvalue(jsonObject.getString("returnMsg"));
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Error sync: ")
                        .append(syncMsgExtra.getProgramvalue())
                        .append(", ")));
            }

            if (lineError.length() > 0) {
                errorMessages.append("Header Data: ").append(invCountHeaderDTO.getCountHeaderId()).append(", ")
                        .append(lineError).append("\n");
            }
        }

        invCountInfoDTO.setListErrMsg(
                invCountHeaderDTOS.stream()
                        .filter(header -> header.getErrMsg() != null)
                        .collect(Collectors.toList())
        );

        invCountInfoDTO.setErrMsg(errorMessages.toString());

        if (errorMessages.length() == 0 ) {
            invCountInfoDTO.setSuccessMsg("Success");
            invCountInfoDTO.setListSuccessMsg(invCountHeaderDTOS);
        }

        return invCountInfoDTO;
    }

    public ResponsePayloadDTO invokeWms(InvCountHeaderDTO invCountHeaderDTO) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setPayload(JSON.toJSONString(invCountHeaderDTO));
        return  interfaceInvokeSdk.invoke(invCountHeaderDTO.getNamespace(), invCountHeaderDTO.getServerCode(),
                invCountHeaderDTO.getInterfaceCode(), requestPayloadDTO);
    }


    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO) {
        Integer isWMS = new InvWarehouse().setWarehouseId(invCountHeaderDTO.getWarehouseId()).getIsWmsWarehouse();
        String errorMsg;
        String status;

        if (isWMS==1) {
            errorMsg = "The current warehouse is not a WMS warehouse, operations are not allowed";
            status="E";
            invCountHeaderDTO.setErrMsg(status + errorMsg);
        }

        List<InvCountLine> lineFromDB = invCountLineRepository.select(InvCountLine.FIELD_COUNT_HEADER_ID,
                invCountHeaderDTO.getCountHeaderId());

        if (invCountHeaderDTO.getCountOrderLineList().size() != lineFromDB.size()) {
            errorMsg = "The counting order line data is inconsistent with the INV system, please check the data";
            status="E";
            invCountHeaderDTO.setErrMsg(status + errorMsg);
        }

        Set<Long> lineIdsFromDTO = invCountHeaderDTO.getCountOrderLineList().stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toSet());

        Set<Long> lineIdsFromDB = lineFromDB.stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toSet());

        Set<Long> missingInDB = lineIdsFromDTO.stream()
                .filter(id -> !lineIdsFromDB.contains(id))
                .collect(Collectors.toSet());

        Set<Long> missingInDTO = lineIdsFromDB.stream()
                .filter(id -> !lineIdsFromDTO.contains(id))
                .collect(Collectors.toSet());

        if (!missingInDB.isEmpty() || !missingInDTO.isEmpty()) {
            errorMsg = "The counting order line data is inconsistent with the INV system, please check the data";
            status="E";
            invCountHeaderDTO.setErrMsg(status + errorMsg);
        }


        // Check data consistency
//                If(The number of rows is inconsistent or lineId can not match) {
//                    errorMsg = "The counting order line data is inconsistent with the INV system, please check the data";
//                    status="E";
//                }
        // Update the line data
//                updateLines(including unitQty,unitDiffQty,remark)



        return invCountHeaderDTO;
    }
}

