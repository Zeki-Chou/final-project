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
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.TokenUtils;
import org.hzero.mybatis.domian.Condition;
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    private ProfileClient profileClient;

    @Autowired
    private WorkflowClient workflowClient;

    @Autowired
    private InvStockRepository invStockRepository;


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
//        List<LovValueDTO> validStatusList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_STATUS,
//                BaseConstants.DEFAULT_TENANT_ID);
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);

        List<String> allowedStatuses = Arrays.asList(
                "INCOUNTING",
                "DRAFT",
                "REJECTED",
                "WITHDRAWN"
        );
//        List<String> validStatuses = validStatusList.stream()
//                .map(LovValueDTO::getValue)
//                .collect(Collectors.toList());
        List<Long> warehouseWMSIds  = invWarehouseRepository.selectList(new InvWarehouse().setIsWmsWarehouse(1)).stream()
                .map(InvWarehouse::getWarehouseId).collect(Collectors.toList());

        StringBuilder errorMessages = new StringBuilder();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        try {
            for (InvCountHeaderDTO data : invCountHeaderDTOS) {
                StringBuilder lineError = new StringBuilder();
                InvCountHeader header = invCountHeaderRepository.selectOne(data);

                if (data.getCountHeaderId() != null) {
                    if (!allowedStatuses.contains(data.getCountStatus())) {
                        data.setErrMsg(String.valueOf(lineError.append("Data status allowed: only draft, in counting," +
                                " rejected, and withdrawn status can be modified (").append(data.getCountHeaderId()).append("), ")));
                    }

                    if (Objects.equals(data.getCountStatus(), "DRAFT") &&  data.getCreatedBy() != jsonObject.getLong("id")) {
                        data.setErrMsg(String.valueOf(lineError.append("Document in draft status can only be modified " +
                                "by the document creator. (").append(data.getCountHeaderId()).append("), ")));
                    }

                    if (Arrays.asList("INCOUNTING", "REJECTED", "WITHDRAWN").contains(data.getCountStatus())) {
                        if (warehouseWMSIds.contains(data.getWarehouseId()) &&
                                !Arrays.asList(data.getSupervisorIds().split(",")).contains(String.valueOf(jsonObject.getLong("id")))) {
                            data.setErrMsg(String.valueOf(
                                    lineError.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate. (")
                                            .append(data.getCountHeaderId()).append("), ")
                            ));
                        }

                        if ((data.getSupervisorIds() == null ||
                                !Arrays.asList(data.getSupervisorIds().split(","))
                                        .contains(String.valueOf(jsonObject.getLong("id")))) &&
                                (data.getCounterIds() == null ||
                                        !Arrays.asList(data.getCounterIds().split(","))
                                                .contains(String.valueOf(jsonObject.getLong("id")))) &&
                                jsonObject.getLong("id") != data.getCreatedBy())
                        {
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
        } catch (Exception e) {
            throw new CommonException(e);
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

        List<InvCountHeader> listInsert = invCountHeaderRepository.batchInsertSelective(insertList);

        updateState(updateList);

        List<InvCountHeaderDTO> dtos = new ArrayList<>();
        listInsert.forEach(data -> {
            InvCountHeaderDTO dto = new InvCountHeaderDTO();
            BeanUtils.copyProperties(data, dto);
            dtos.add(dto);
        });

        updateList.forEach(data -> {
            InvCountHeaderDTO dto = new InvCountHeaderDTO();
            BeanUtils.copyProperties(data, dto);
            dtos.add(dto);
        });
        return dtos;
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
    public InvCountHeaderDTO detail(Long headerId) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(headerId);

        if (invCountHeader == null) {
            throw new CommonException("OrderHeader not found for id: " + headerId);
        }

        InvCountHeaderDTO dto = new InvCountHeaderDTO();
        BeanUtils.copyProperties(invCountHeader, dto);

        String counterIds = dto.getCounterIds();
        String supervisorIds = (dto.getSupervisorIds() != null) ? dto.getSupervisorIds() : "";
        String materialIds = (dto.getSnapshotMaterialIds() != null) ? dto.getSnapshotMaterialIds() : "";
        String batchIds = (dto.getSnapshotBatchIds() != null) ? dto.getSnapshotBatchIds() : "";


        List<IamDTO> counterList;
        List<IamDTO> supervisorList = new ArrayList<>();
        List<InvMaterialDTO> materialDTOS = new ArrayList<>();
        List<InvBatchDTO> batchDTOS = new ArrayList<>();


        if (counterIds != null && !counterIds.trim().isEmpty()) {
            counterList = Arrays.stream(counterIds.split(","))
                    .map(String::trim)
                    .filter(counterId -> !counterId.isEmpty())
                    .map(Long::valueOf)
                    .map(counterId -> {
                        IamDTO counter = new IamDTO();
                        counter.setId(counterId);
                        return counter;
                    })
                    .collect(Collectors.toList());
        } else {
            counterList = Collections.emptyList();
        }

        if (!supervisorIds.trim().isEmpty()) {
            supervisorList = Arrays.stream(supervisorIds.split(","))
                    .map(String::trim)
                    .filter(supervisorId -> !supervisorId.isEmpty())
                    .map(supervisorId -> {
                        IamDTO supervisor = new IamDTO();
                        supervisor.setId(Long.valueOf(supervisorId)); // Set the ID
                        return supervisor;
                    })
                    .collect(Collectors.toList());
        } else {
            supervisorList = Collections.emptyList();
        }

        List<InvMaterial> materialListData =  invMaterialRepository.selectByIds(materialIds);
        if (!materialListData.isEmpty()) {
            for (InvMaterial invMaterial : materialListData) {
                InvMaterialDTO materialDTO = new InvMaterialDTO();
                materialDTO.setMaterialId(invMaterial.getMaterialId());
                materialDTO.setMaterialCode(invMaterial.getMaterialCode());
                materialDTO.setMaterialName(invMaterial.getMaterialName());
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
        List<InvCountLine> listLine = invCountLineRepository.select("countHeaderId", headerId);
//        List<InvCountLineDTO> lineDTOS = new ArrayList<>();
//
//        for (InvCountLine dtoLine : listLine) {
//            InvCountLineDTO line = new InvCountLineDTO();
//            BeanUtils.copyProperties(dtoLine, line);
//            line.setMaterialName(
//                    materialDTOS.stream()
//                            .filter(material -> material.getMaterialId().equals(line.getMaterialId()))
//                            .map(InvMaterial::getMaterialName)
//                            .findFirst()
//                            .orElse(null)
//            );
//
//            line.setMaterialCode(
//                    materialDTOS.stream()
//                            .filter(material -> material.getMaterialId().equals(line.getMaterialId()))
//                            .map(InvMaterial::getMaterialCode)
//                            .findFirst()
//                            .orElse(null)
//            );
//            line.setBatchCode(
//                    batchDTOS.stream()
//                            .filter(material -> material.getBatchId().equals(line.getBatchId()))
//                            .map(InvBatch::getBatchCode)
//                            .findFirst()
//                            .orElse(null)
//            );
//            line.setCounter(
//                    Arrays.stream(line.getCounterIds().split(","))
//                            .map(String::trim)
//                            .filter(counterId -> !counterId.isEmpty())
//                            .map(Long::valueOf)
//                            .map(counterId -> {
//                                IamDTO counter = new IamDTO();
//                                counter.setId(counterId);
//                                return counter;
//                            })
//                            .collect(Collectors.toList())
//            );
//
//
//
//
//            lineDTOS.add(line);
//        }


        dto.setCounterList(counterList);
        dto.setSupervisorList(supervisorList);
        dto.setSnapshotMaterialList(materialDTOS);
        dto.setSnapshotBatchList(batchDTOS);
        dto.setWMSwarehouse(isWMS == 1);
        dto.setCountOrderLineList(listLine);
//        dto.setCountOrderLineListDTO(lineDTOS);

        return dto;
    }

    private void updateState(List<InvCountHeader> invCountHeaders) {
        invCountHeaderRepository.batchUpdateOptional(invCountHeaders.stream().filter(data ->
                        Objects.equals(data.getCountStatus(), "INCOUNTING")).collect(Collectors.toList()),
                InvCountHeader.FIELD_REMARK, InvCountHeader.FIELD_REASON);
        invCountHeaderRepository.batchUpdateOptional(invCountHeaders.stream().filter(data ->
                        Objects.equals(data.getCountStatus(), "REJECTED")).collect(Collectors.toList()),
                InvCountHeader.FIELD_REASON);
//        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders.stream().filter(data ->
//                Objects.equals(data.getCountStatus(), "DRAFT")).collect(Collectors.toList()));
    }

    @Transactional
    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        StringBuilder errorMessages = new StringBuilder();
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);
        String headerIds = invCountHeaderDTOS.stream().map(InvCountHeaderDTO::getCountHeaderId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        List<InvCountHeader> listHeader = invCountHeaderRepository.selectByIds(headerIds);
        List<InvCountHeaderDTO> listHeaderDTO = new ArrayList<>();

        for (InvCountHeader header : listHeader) {
           InvCountHeaderDTO dto = new InvCountHeaderDTO();
           BeanUtils.copyProperties(header, dto);
           listHeaderDTO.add(dto);
       }

        List<LovValueDTO> validDimensionTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_DIMENSION,
                BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> validCountTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_TYPE,
                BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> validModeTypesList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_MODE,
                BaseConstants.DEFAULT_TENANT_ID);

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


        for (InvCountHeaderDTO invCountHeaderDTO: listHeaderDTO) {
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
//            if (!validStatusList.contains(invCountHeaderDTO.getCountStatus())) {
//                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Status wrong: ")
//                        .append(invCountHeaderDTO.getCountStatus())
//                        .append(", ")));
//            }
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

            if (invCountHeaderDTO.getCountType().equals("MONTH")) {
                LocalDateTime dateTime = LocalDateTime.ofInstant(invCountHeaderDTO.getCreationDate().toInstant(), ZoneId.systemDefault());
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
                invCountHeaderDTO.setCountTimeStr(dateTime.format(outputFormatter));
            }

            if (invCountHeaderDTO.getCountType().equals("YEAR")) {
                LocalDateTime dateTime = LocalDateTime.ofInstant(invCountHeaderDTO.getCreationDate().toInstant(), ZoneId.systemDefault());
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy");
                invCountHeaderDTO.setCountTimeStr(dateTime.format(outputFormatter));
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

    @Transactional
    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<InvCountLineDTO> lines = new ArrayList<>();
        invCountHeaderDTOS.forEach(header -> {
            header.setCountStatus("INCOUNTING");
        });

        List<InvCountHeader> invCountHeaders = new ArrayList<>(invCountHeaderDTOS);
        invCountHeaders = invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders);

        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        LocalDate currentDate = LocalDate.now();


        LocalDateTime localDateTime = currentDate.atStartOfDay();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateString = localDateTime.format(formatter);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date formattedDate = sdf.parse(formattedDateString);
            criteria.orGreaterThanOrEqualTo(InvCountLine.FIELD_CREATION_DATE, formattedDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }


        List<InvCountLine> listLines = invCountLineRepository.selectByCondition(condition);

//        List<InvCountLine> listLines = invCountLineRepository.selectAll();
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
            InvStockDTO invStockDTO = new InvStockDTO();
            invStockDTO.setDimension(header.getCountDimension());
            invStockDTO.setTenantId(header.getTenantId());
            invStockDTO.setCompanyId(header.getCompanyId());
            invStockDTO.setDepartmentId(header.getDepartmentId());
            invStockDTO.setSnapshotMaterialList(invMaterialRepository.selectByIds(header.getSnapshotMaterialIds()).stream().map(InvMaterial::getMaterialId).collect(Collectors.toList()));
            invStockDTO.setSnapshotBatchList(invBatchRepository.selectByIds(header.getSnapshotBatchIds()).stream().map(InvBatch::getBatchId).collect(Collectors.toList()));

            List<InvStock> listStock = invStockMapper.getListForQuantity(invStockDTO);
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
        List<InvCountExtra> listExtra = new ArrayList<>();

        String headerIds = invCountHeaderDTOS.stream().map(InvCountHeaderDTO::getCountHeaderId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        List<InvCountHeader> listHeader = invCountHeaderRepository.selectByIds(headerIds);
        List<InvCountHeaderDTO> listHeaderDTO = new ArrayList<>();

        for (InvCountHeader header : listHeader) {
            InvCountHeaderDTO dto = new InvCountHeaderDTO();
            List<InvCountLine> listLine = invCountLineRepository.select(InvCountLine.FIELD_COUNT_HEADER_ID, header.getCountHeaderId());
            dto.setCountOrderLineList(listLine);
            BeanUtils.copyProperties(header, dto);
            listHeaderDTO.add(dto);
        }


        for (InvCountHeaderDTO invCountHeaderDTO : listHeaderDTO) {
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
                syncMsgExtra.setProgramvalue("SKIP");
            }

            invCountHeaderDTO.setEmployeeNumber("47358");
            invCountHeaderDTO.setNamespace("HZERO");
            invCountHeaderDTO.setServerCode("FEXAM_WMS");
            invCountHeaderDTO.setInterfaceCode("fexam-wms-api.thirdAddCounting");
            ResponsePayloadDTO response =  invokeWms(invCountHeaderDTO);


            if (response.getBody() == null) {
                throw new CommonException("Response from external is null");
            }

            JSONObject jsonObject = new JSONObject(response.getPayload());

            if (Objects.equals(jsonObject.getString("returnStatus"), "S")) {
                syncStatusExtra.setProgramvalue("SUCCESS");
                syncStatusExtra.setSourceid(invCountHeaderDTO.getCountHeaderId());
                syncMsgExtra.setProgramvalue("");
                syncMsgExtra.setSourceid(invCountHeaderDTO.getCountHeaderId());
                invCountHeaderDTO.setRelatedWmsOrderCode(jsonObject.getString("code"));
                invCountHeaderRepository.updateOptional(invCountHeaderDTO, InvCountHeaderDTO.FIELD_RELATED_WMS_ORDER_CODE);
            } else {
                syncStatusExtra.setProgramvalue("ERROR");
                syncStatusExtra.setSourceid(invCountHeaderDTO.getCountHeaderId());
                syncMsgExtra.setProgramvalue(jsonObject.getString("returnMsg"));
                syncMsgExtra.setSourceid(invCountHeaderDTO.getCountHeaderId());
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Error sync: ")
                        .append(syncMsgExtra.getProgramvalue())
                        .append(", ")));
            }

            if (lineError.length() > 0) {
                errorMessages.append("Header Data: ").append(invCountHeaderDTO.getCountHeaderId()).append(", ")
                        .append(lineError).append("\n");
            }

            listExtra.add(syncStatusExtra);
            listExtra.add(syncMsgExtra);
        }

        invCountExtraRepository.batchInsertSelective(listExtra);

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
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("Authorization", "bearer " + TokenUtils.getToken());
        requestPayloadDTO.setHeaderParamMap(paramMap);
        requestPayloadDTO.setPayload(JSON.toJSONString(invCountHeaderDTO));
        requestPayloadDTO.setMediaType("application/json");
        return  interfaceInvokeSdk.invoke(invCountHeaderDTO.getNamespace(), invCountHeaderDTO.getServerCode(),
                invCountHeaderDTO.getInterfaceCode(), requestPayloadDTO);
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO) {
        InvCountHeader header = invCountHeaderRepository.selectOne(new InvCountHeader().setCountNumber(invCountHeaderDTO.getCountNumber()));
        Integer isWMS = invWarehouseRepository.selectOne(new InvWarehouse().setWarehouseId(header.getWarehouseId())).getIsWmsWarehouse();
        String errorMsg;
        String status;
        List<InvCountLine> linesFromDB = invCountLineRepository.select("countHeaderId", header.getCountHeaderId());

        if (isWMS!=1) {
            errorMsg = "The current warehouse is not a WMS warehouse, operations are not allowed";
            status="E";
            invCountHeaderDTO.setErrMsg(errorMsg);
            invCountHeaderDTO.setStatus(status);
        }

        if (invCountHeaderDTO.getCountOrderLineList().size() != linesFromDB.size()) {
            errorMsg = "The counting order line data is inconsistent with the INV system, please check the data";
            status="E";
            invCountHeaderDTO.setErrMsg(invCountHeaderDTO.getErrMsg() + errorMsg);
            invCountHeaderDTO.setErrMsg(status);
        }

        Set<Long> lineIdsFromDTO = invCountHeaderDTO.getCountOrderLineList().stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toSet());

        Set<Long> lineIdsFromDB = linesFromDB.stream()
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
            invCountHeaderDTO.setErrMsg(invCountHeaderDTO.getErrMsg() + errorMsg);
            invCountHeaderDTO.setStatus(status);
        }

        Map<Long, BigDecimal> unitQtyDTO = invCountHeaderDTO.getCountOrderLineList().stream()
                .collect(Collectors.toMap(InvCountLine::getCountLineId, InvCountLine::getUnitQty));

        if (invCountHeaderDTO.getErrMsg() != null) {
            throw new CommonException(invCountHeaderDTO.getErrMsg());
        }

        linesFromDB.forEach(data -> {
            data.setUnitQty(unitQtyDTO.get(data.getCountLineId()));
            data.setUnitDiffQty(data.getUnitQty().subtract(data.getSnapshotUnitQty()));
        });

        List<InvCountLineDTO> lineDTOS = new ArrayList<>();
        for (InvCountLine line : linesFromDB) {
            InvCountLineDTO dto = new InvCountLineDTO();
            BeanUtils.copyProperties(line, dto);
            lineDTOS.add(dto);
        }

//        invCountLineRepository.batchUpdateByPrimaryKeySelective(invCountHeaderDTO.getCountOrderLineList());
        invCountLineService.saveData(lineDTOS);
        invCountHeaderDTO.setStatus("S");

        List<InvCountLine> lineData = new ArrayList<>();
        for (InvCountLineDTO dto : lineDTOS) {
            InvCountLine data = new InvCountLine();
            BeanUtils.copyProperties(dto, data);
            lineData.add(data);
        }
        invCountHeaderDTO.setCountOrderLineList(lineData);

        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<String> allowedStatuses = Arrays.asList(
                "INCOUNTING",
                "PROCESSING",
                "REJECTED",
                "WITHDRAWN"
        );
        String remoteResponse = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject = new JSONObject(remoteResponse);

//        List<String> supervisorIds = invCountHeaderDTOS.stream().map(InvCountHeaderDTO::getSupervisorIds)
//                .collect(Collectors.toList());

        String supervisorIds = invCountHeaderDTOS.stream()
                .map(InvCountHeaderDTO::getSupervisorIds)
                .collect(Collectors.joining(","));

        String id = String.valueOf(jsonObject.getLong("id"));

        boolean isSupervisor = Arrays.asList(supervisorIds.split(",")).contains(id);

        StringBuilder errorMessages = new StringBuilder();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        String headerIds = invCountHeaderDTOS.stream().map(InvCountHeaderDTO::getCountHeaderId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        List<InvCountHeader> listHeader = invCountHeaderRepository.selectByIds(headerIds);
        List<InvCountHeaderDTO> listHeaderDTO = new ArrayList<>();
        Map<Long, List<InvCountLine>> lineMap = invCountLineRepository.selectByIds(headerIds).stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));

        for (InvCountHeader header : listHeader) {
            InvCountHeaderDTO dto = new InvCountHeaderDTO();
            BeanUtils.copyProperties(header, dto);
//            dto.setCountOrderLineList(invCountLineRepository.select(InvCountLine.FIELD_COUNT_HEADER_ID,
//                    header.getCountHeaderId()));
            dto.setCountOrderLineList(lineMap.get(dto.getCountHeaderId()));
            listHeaderDTO.add(dto);
        }

        for (InvCountHeaderDTO invCountHeaderDTO : listHeaderDTO) {
            StringBuilder lineError = new StringBuilder();

            if (!allowedStatuses.contains(invCountHeaderDTO.getCountStatus())) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Draft status not allowed to submit: ")
                        .append(invCountHeaderDTO.getCountStatus())
                        .append(", ")));
            }
//            String id = String.valueOf(jsonObject.getLong("id"));
            if (!isSupervisor) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("Only supervisor that allowed to submit")
                        .append(", ")));
            }

            long countNull = invCountHeaderDTO.getCountOrderLineList().stream().filter(data -> data.getUnitQty() == null)
                    .count();


            if (countNull > 0) {
                invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("There are data rows with empty count quantity. Please check the data.: ")
                        .append(invCountHeaderDTO.getCountOrderLineList())
                        .append(", ")));
            }

            long countReasonWithDiffQty = invCountHeaderDTO.getCountOrderLineList().stream().filter(data ->
                    data.getUnitDiffQty().compareTo(BigDecimal.ZERO) != 0).count();

            if (countReasonWithDiffQty == 0) {
                if (invCountHeaderDTO.getReason().isEmpty()) {
                    invCountHeaderDTO.setErrMsg(String.valueOf(lineError.append("There's diff but reason still empty: ")
                            .append(invCountHeaderDTO.getCountOrderLineList())
                            .append(", ")));
                }
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

    @Transactional
    @Override
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        String workflowFlag = profileClient.getProfileValueByOptions(BaseConstants.DEFAULT_TENANT_ID, null,
                null, "FEXAM58.INV.COUNTING.ISWORKFLO");

        String departmentIds = invCountHeaderDTOS.stream().map(InvCountHeaderDTO::getDepartmentId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Map<Long, String> listDepartment = iamDepartmentRepository.selectByIds(departmentIds).stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, IamDepartment::getDepartmentCode));

        if (workflowFlag.equals("1")) {
            for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
                Map<String, Object> mapsDepartment = new HashMap<>();
                mapsDepartment.put("departmentCode", listDepartment.get(invCountHeaderDTO.getDepartmentId()));
                workflowClient.startInstanceByFlowKey(DetailsHelper.getUserDetails().getTenantId(),
                        InvCountHeaderConstant.FLOW_KEY, invCountHeaderDTO.getCountNumber(), "EMPLOYEE", "47358",
                        mapsDepartment);
            }
        } else {
            List<InvCountHeader> invCountHeaders = invCountHeaderDTOS.stream().filter(data -> data.getCountHeaderId() != null)
                    .collect(Collectors.toList());
            invCountHeaders.forEach(invCountHeader -> {
                invCountHeader.setCountStatus("CONFIRMED");
            });

            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders);
        }

        return invCountHeaderDTOS;
    }

    @Override
    public InvCountHeaderDTO submitCallback(Long organizationId, WorkFlowEventDTO workFlowEventRequestDTO) {
        Condition condition = new Condition(InvCountHeader.class);

        Condition.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("countNumber", workFlowEventRequestDTO.getBusinessKey());
        List<InvCountHeader> invCountHeaderList =  invCountHeaderRepository.selectByCondition(condition);

        invCountHeaderList.get(0).setApprovedTime(workFlowEventRequestDTO.getApprovedTime());
        invCountHeaderList.get(0).setCountStatus(workFlowEventRequestDTO.getDocStatus());
        invCountHeaderList.get(0).setWorkflowId(workFlowEventRequestDTO.getWorkflowId());
        invCountHeaderRepository.updateByPrimaryKey(invCountHeaderList.get(0));

        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(invCountHeaderList.get(0), invCountHeaderDTO);

        return  invCountHeaderDTO;
    }

    @Override
    public List<RunTaskHistory> approvalHistory(Long organizationId, Long workflowId) {
        return workflowClient.approveHistory(organizationId, workflowId);
    }

    @ProcessCacheValue
    @Override
    public List<InvCountHeaderDTO> getReport(InvCountHeaderDTO invCountHeader) {
        List<InvCountHeaderDTO> headers = invCountHeaderRepository.selectList(invCountHeader);
        String departmentIds = headers.stream().map(InvCountHeader::getDepartmentId)
                .map(String::valueOf).collect(Collectors.joining(","));
        String warehouseIds = headers.stream().map(InvCountHeader::getWarehouseId)
                .map(String::valueOf).collect(Collectors.joining(","));
        Set<String> materialIds = headers.stream()
                .filter(header -> header.getSnapshotMaterialIds() != null)
                .flatMap(header -> Arrays.stream(header.getSnapshotMaterialIds().split(",")))
                .map(String::trim)
                .collect(Collectors.toSet());
        Set<String> batchIds = headers.stream()
                .filter(header -> header.getSnapshotBatchIds() != null) // Ensure it's not null
                .flatMap(header -> Arrays.stream(header.getSnapshotBatchIds().split(",")))
                .map(String::trim)
                .collect(Collectors.toSet());
        Map<Long, String> departmentDTOMap = iamDepartmentRepository.selectByIds(departmentIds).stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, IamDepartment::getDepartmentName));
        Map<Long, String> warehouseDTOMap = invWarehouseRepository.selectByIds(warehouseIds).stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, InvWarehouse::getWarehouseCode));
        Map<Long, String> materialNameDTOMap = invMaterialRepository.selectByIds(String.join(",",materialIds))
                .stream().collect(Collectors.toMap(InvMaterial::getMaterialId, InvMaterial::getMaterialName));
        Map<Long, String> materialCodeDTOMap = invMaterialRepository.selectByIds(String.join(",",materialIds))
                .stream().collect(Collectors.toMap(InvMaterial::getMaterialId, InvMaterial::getMaterialCode));
        Map<Long, String> batchDTOMap = invBatchRepository.selectByIds(String.join(",", batchIds))
                .stream().collect(Collectors.toMap(InvBatch::getBatchId, InvBatch::getBatchCode));

        Set<Long> headerIdSet = headers.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toSet());

        Condition condition = new Condition(InvCountLine.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andIn(InvCountLine.FIELD_COUNT_HEADER_ID, headerIdSet);

        List<InvCountLine> invoiceApplyLines = invCountLineRepository.selectByCondition(condition);

        Map<Long, List<InvCountLine>> mapLineHeaders = invoiceApplyLines.stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));

       headers.forEach(header -> {
            header.setDepartmentName(departmentDTOMap.get(header.getDepartmentId()));
            header.setWarehouseName(warehouseDTOMap.get(header.getWarehouseId()));
       });

       List<InvCountLineDTO> lineDTOS = new ArrayList<>();
       for (InvCountLine line : invoiceApplyLines) {
           InvCountLineDTO dto = new InvCountLineDTO();
           BeanUtils.copyProperties(line, dto);
           dto.setMaterialName(materialNameDTOMap.get(dto.getMaterialId()));
           dto.setMaterialCode(materialCodeDTOMap.get(dto.getMaterialId()));
           dto.setBatchCode(batchDTOMap.get(dto.getBatchId()));
           lineDTOS.add(dto);
       }

       for (InvCountHeaderDTO dto : headers) {
           dto.setCountOrderLineListDTO(lineDTOS.stream().filter(data ->
                   data.getCountHeaderId().equals(dto.getCountHeaderId())).collect(Collectors.toList()));
       }

       return headers;
    }
}

