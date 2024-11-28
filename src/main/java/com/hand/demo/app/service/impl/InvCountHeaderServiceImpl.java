package com.hand.demo.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.enums.Enums;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author Allan
 * @since 2024-11-25 08:19:18
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {

    private final InvCountHeaderRepository invCountHeaderRepository;
    private final CodeRuleBuilder codeRuleBuilder;
    private final IamRemoteService iamRemoteService;
    private final InvWarehouseRepository invWarehouseRepository;
    private final LovAdapter lovAdapter;
    private final IamCompanyService iamCompanyService;
    private final IamDepartmentService iamDepartmentService;
    private final InvWarehouseService invWarehouseService;
    private final InvStockRepository invStockRepository;
    private final InvCountLineService invCountLineService;
    private final InvCountExtraRepository invCountExtraRepository;
    private final InvCountLineRepository invCountLineRepository;
    private final InvCountExtraService invCountExtraService;
    private final InterfaceInvokeSdk interfaceInvokeSdk;
    private final InvBatchService invBatchService;
    private final InvMaterialService invMaterialService;

    public InvCountHeaderServiceImpl(
            InvCountHeaderRepository invCountHeaderRepository,
            CodeRuleBuilder codeRuleBuilder,
            IamRemoteService iamRemoteService,
            InvWarehouseRepository invWarehouseRepository,
            LovAdapter lovAdapter,
            IamCompanyService iamCompanyService,
            IamDepartmentService iamDepartmentService,
            InvWarehouseService invWarehouseService,
            InvStockRepository invStockRepository,
            InvCountLineService invCountLineService,
            InvCountExtraRepository invCountExtraRepository,
            InvCountLineRepository invCountLineRepository,
            InvCountExtraService invCountExtraService,
            InterfaceInvokeSdk interfaceInvokeSdk,
            InvBatchService invBatchService,
            InvMaterialService invMaterialService
    ) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.codeRuleBuilder = codeRuleBuilder;
        this.iamRemoteService = iamRemoteService;
        this.invWarehouseRepository = invWarehouseRepository;
        this.lovAdapter = lovAdapter;
        this.iamCompanyService = iamCompanyService;
        this.iamDepartmentService = iamDepartmentService;
        this.invWarehouseService = invWarehouseService;
        this.invStockRepository = invStockRepository;
        this.invCountLineService = invCountLineService;
        this.invCountExtraRepository = invCountExtraRepository;
        this.invCountLineRepository = invCountLineRepository;
        this.invCountExtraService = invCountExtraService;
        this.interfaceInvokeSdk = interfaceInvokeSdk;
        this.invBatchService = invBatchService;
        this.invMaterialService = invMaterialService;
    }

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        insertList.forEach(header -> {
            header.setCountStatus(Enums.InvCountHeader.Status.DRAFT.name());
            header.setCountNumber(generateCountNumber());
            header.setDelFlag(BaseConstants.Flag.NO);
        });

        List<InvCountHeader> draftUpdateList = filterHeaderListByStatus(updateList, Enums.InvCountHeader.Status.DRAFT.name());
        List<InvCountHeader> inCountingUpdateList = filterHeaderListByStatus(updateList, Enums.InvCountHeader.Status.INCOUNTING.name());
        List<InvCountHeader> rejectedUpdateList = filterHeaderListByStatus(updateList, Enums.InvCountHeader.Status.REJECTED.name());

        invCountHeaderRepository.batchInsertSelective(insertList);
        invCountHeaderRepository.batchUpdateOptional(inCountingUpdateList, InvCountHeader.FIELD_REMARK, InvCountHeader.FIELD_REASON);
        invCountHeaderRepository.batchUpdateOptional(rejectedUpdateList, InvCountHeader.FIELD_REASON);

        draftUpdateList.forEach(header -> {
            header.setRemark(null);
            header.setReason(null);
        });

        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(draftUpdateList);
        return invCountHeaders;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validHeaderDTO = new ArrayList<>();
        List<InvCountHeaderDTO> invalidHeaderDTO = new ArrayList<>();
        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long currentUser = iamJSONObject.getLong(Constants.Iam.FIELD_ID);

        invCountHeaderDTOS.forEach(header -> {
            if (!header.getCountStatus().equals(Enums.InvCountHeader.Status.DRAFT.name()) || !currentUser.equals(header.getCreatedBy())) {
                invalidHeaderDTO.add(header);
            } else {
                validHeaderDTO.add(header);
            }
        });

        invCountInfoDTO.setValidHeaderDTOS(validHeaderDTO);
        invCountInfoDTO.setInvalidHeaderDTOS(invalidHeaderDTO);
        invCountInfoDTO.setErrSize(invalidHeaderDTO.size());
        return invCountInfoDTO;
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        List<Long> wmsWarehouseIds = invWarehouseService.getWMSWarehouseIds();
        InvCountHeader header = invCountHeaderRepository.selectByPrimary(countHeaderId);

        if (header == null) {
            throw new CommonException("header not found");
        }

        // map to dto
        InvCountHeaderDTO dto = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, dto);

        if (wmsWarehouseIds.contains(dto.getWarehouseId())) {
            dto.setIsWmsWarehouse(BaseConstants.Flag.YES);
        } else {
            dto.setIsWmsWarehouse(BaseConstants.Flag.NO);
        }

        List<UserInfoDTO> counterList = convertUserIdToList(dto.getCounterIds());
        List<UserInfoDTO> supervisorList = convertUserIdToList(dto.getSupervisorIds());
        List<MaterialInfoDTO> materialInfoDTOList = invMaterialService.convertMaterialIdsToList(dto.getSnapshotMaterialIds());
        List<BatchInfoDTO> batchInfoDTOList = invBatchService.convertBatchIdsToList(dto.getSnapshotBatchIds());

        dto.setCounterList(counterList);
        dto.setSupervisorList(supervisorList);
        dto.setSnapshotBatchList(batchInfoDTOList);
        dto.setSnapshotMaterialList(materialInfoDTOList);
        return dto;
    }

    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaders) {
        List<InvCountHeaderDTO> invalidDTO = new ArrayList<>();
        List<InvCountHeaderDTO> validDTO = new ArrayList<>();
        InvCountInfoDTO countInfoDTO = new InvCountInfoDTO();

        // get creator id
        JSONObject iamJson = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJson.getLong(Constants.Iam.FIELD_ID);

        // get lov values
        Long tenantId = BaseConstants.DEFAULT_TENANT_ID;
        List<String> lovStatusValues = getLovValues(Constants.InvCountHeader.STATUS_LOV_CODE, tenantId);
        List<String> lovCountDimensionValues = getLovValues(Constants.InvCountHeader.COUNT_DIMENSION_LOV_CODE, tenantId);
        List<String> lovCountModeValues = getLovValues(Constants.InvCountHeader.COUNT_MODE_LOV_CODE, tenantId);
        List<String> lovCountTypeValues = getLovValues(Constants.InvCountHeader.COUNT_TYPE_LOV_CODE, tenantId);

        //query the database based on the input document ID
        //company
        List<Long> companyIdList = invCountHeaders.stream().map(InvCountHeader::getCompanyId).collect(Collectors.toList());
        List<Long> companiesIds = iamCompanyService.findCompanyIds(companyIdList);

        //warehouse
        List<Long> warehouseIdList = invCountHeaders.stream().map(InvCountHeader::getWarehouseId).collect(Collectors.toList());
        List<Long> warehousesIds = invWarehouseService.findByIds(warehouseIdList);

        //department
        List<Long> departmentIdList = invCountHeaders.stream().map(InvCountHeader::getCompanyId).collect(Collectors.toList());
        List<Long> departmentsIds = iamDepartmentService.findByIds(departmentIdList);

        for (InvCountHeaderDTO header: invCountHeaders) {
            InvStockDTO invStockDTO = new InvStockDTO();
            invStockDTO.setCompanyId(header.getCompanyId());
            invStockDTO.setDepartmentId(header.getDepartmentId());
            invStockDTO.setWarehouseId(header.getWarehouseId());
            invStockDTO.setMaterialIds(Utils.convertStringIdstoList(header.getSnapshotMaterialIds()));

            // Query on hand quantity based on the
            // tenant+company+department+warehouse+material+on hand quantity not equals 0
            BigDecimal onHandQty = invStockRepository.getSumOnHandQty(invStockDTO);

            if (!header.getCountStatus().equals(Enums.InvCountHeader.Status.DRAFT.name())) {
                header.setErrorMessage("Only draft status can execute");
            } else if (!userId.equals(header.getCreatedBy())) {
                header.setErrorMessage("Only the document creator can execute");
            } else if (!lovStatusValues.contains(header.getCountStatus())) {
                header.setErrorMessage("invalid count status");
            } else if (!lovCountDimensionValues.contains(header.getCountDimension())) {
                header.setErrorMessage("invalid count dimension");
            } else if (!lovCountTypeValues.contains(header.getCountType())) {
                header.setErrorMessage("invalid count type");
            } else if (!lovCountModeValues.contains(header.getCountMode())) {
                header.setErrorMessage("invalid count mode");
            } else if (!companiesIds.contains(header.getCompanyId())) {
                header.setErrorMessage("invalid company");
            } else if (!departmentsIds.contains(header.getDepartmentId())) {
                header.setErrorMessage("invalid department");
            } else if (!warehousesIds.contains(header.getWarehouseId())) {
                header.setErrorMessage("invalid warehouse");
            } else if (onHandQty == null) {
                header.setErrorMessage("Unable to query on hand quantity data");
            }

            if (header.getErrorMessage().isEmpty()) {
                validDTO.add(header);
            } else {
                invalidDTO.add(header);
            }
        }

        countInfoDTO.setValidHeaderDTOS(validDTO);
        countInfoDTO.setInvalidHeaderDTOS(invalidDTO);
        countInfoDTO.setErrSize(invalidDTO.size());
        return countInfoDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderList) {
        invCountHeaderList.forEach(header -> header.setCountStatus(Enums.InvCountHeader.Status.INCOUNTING.name()));
        List<InvCountHeaderDTO> insertResult = manualSave(invCountHeaderList);
        List<InvStock> stocks = invStockRepository.selectStocksByHeaders(insertResult);
        //for each header, create list of lines using stock data
        List<InvCountLineDTO> countLinesToInsert = new ArrayList<>();
        insertResult.forEach(header -> {
            List<InvCountLineDTO> invCountLines = this.generateCountLinesFromStocks(header, stocks);
            countLinesToInsert.addAll(invCountLines);
        });
        invCountLineService.saveData(countLinesToInsert);
        return insertResult;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<Long> warehouseWMSIds = invWarehouseService.getWMSWarehouseIds();
        List<String> validUpdateStatuses = getValidCountStatus();

        String draftValue = Enums.InvCountHeader.Status.DRAFT.name();
        String inCountingValue = Enums.InvCountHeader.Status.INCOUNTING.name();
        String rejectedValue = Enums.InvCountHeader.Status.REJECTED.name();
        String withdrawnValue = Enums.InvCountHeader.Status.WITHDRAWN.name();

        List<String> validUpdateStatusSupervisorWMS = validUpdateStatuses
                .stream()
                .filter(status ->   status.equals(inCountingValue) ||
                                    status.equals(rejectedValue) ||
                                    status.equals(withdrawnValue))
                .collect(Collectors.toList());

        List<InvCountHeaderDTO> invalidHeaderDTOS = new ArrayList<>();
        List<InvCountHeaderDTO> validHeaderDTOS = new ArrayList<>();

        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSONObject.getLong(Constants.Iam.FIELD_ID);

        // business verifications
        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {
            List<Long> headerCounterIds = Utils.convertStringIdstoList(invCountHeaderDTO.getCounterIds());
            List<Long> supervisorIds = Utils.convertStringIdstoList(invCountHeaderDTO.getSupervisorIds());

            if (invCountHeaderDTO.getCountHeaderId() == null) {
                validHeaderDTOS.add(invCountHeaderDTO);
            } else if (!validUpdateStatuses.contains(invCountHeaderDTO.getCountStatus())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_STATUS_INVALID);

            } else if (draftValue.equals(invCountHeaderDTO.getCountStatus()) && userId.equals(invCountHeaderDTO.getCreatedBy())) {

                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_ACCESS_INVALID);

            } else if (validUpdateStatusSupervisorWMS.contains(invCountHeaderDTO.getCountStatus())) {

                if (warehouseWMSIds.contains(invCountHeaderDTO.getWarehouseId()) && !supervisorIds.contains(userId)) {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.WAREHOUSE_SUPERVISOR_INVALID);
                } else if (!headerCounterIds.contains(userId)&& !supervisorIds.contains(userId) && !invCountHeaderDTO.getCreatedBy().equals(userId)) {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.ACCESS_UPDATE_STATUS_INVALID);
                }

            }

            if (invCountHeaderDTO.getErrorMessage().isEmpty()) {
                validHeaderDTOS.add(invCountHeaderDTO);
            } else {
                invalidHeaderDTOS.add(invCountHeaderDTO);
            }
        }

        invCountInfoDTO.setInvalidHeaderDTOS(invalidHeaderDTOS);
        invCountInfoDTO.setValidHeaderDTOS(validHeaderDTOS);
        invCountInfoDTO.setErrSize(invalidHeaderDTOS.size());
        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList) {

        InvCountInfoDTO countInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validDTO = new ArrayList<>();
        List<InvCountHeaderDTO> invalidDTO = new ArrayList<>();

        //Query warehouse data based on warehouseCode
        List<Long> warehouseIds = invCountHeaderList.stream().map(InvCountHeaderDTO::getWarehouseId).collect(Collectors.toList());
        List<InvWarehouse> warehouses = invWarehouseRepository.selectByIds(Utils.generateStringIds(warehouseIds));
        List<Long> headerIds = invCountHeaderList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        List<InvCountLineDTO> countLineDTOList = invCountLineRepository.selectByCountHeaderIds(headerIds);

        // for syncStatus and syncMessage to be updated/inserted
        List<InvCountExtra> invCountExtras = new ArrayList<>();

        for (InvCountHeaderDTO invCountHeader: invCountHeaderList) {

            // Determine whether the warehouse data exists by tenantId and warehouseCode
            List<InvWarehouse> warehouseFilterList = warehouses
                    .stream()
                    .filter(warehouse -> warehouse.getWarehouseId().equals(invCountHeader.getWarehouseId()) &&
                                         warehouse.getTenantId().equals(invCountHeader.getTenantId()))
                    .collect(Collectors.toList());

            if (warehouseFilterList.isEmpty()) {
                throw new CommonException("warehouse not found");
            }

            //Get the extended table data based on the counting header ID
            InvCountExtra extraRecord = new InvCountExtra();
            extraRecord.setSourceid(invCountHeader.getCountHeaderId());
            extraRecord.setEnabledflag(BaseConstants.Flag.YES);
            List<InvCountExtra> extras = invCountExtraRepository.selectList(extraRecord);

            InvCountExtra syncStatusExtra;
            InvCountExtra syncMsgExtra;
            if (extras.isEmpty()) {
                syncStatusExtra = new InvCountExtra(
                        BaseConstants.Flag.YES,
                        Constants.InvCountExtra.PROGRAM_KEY_WMS_SYNC_STATUS,
                        invCountHeader.getCountHeaderId(),
                        invCountHeader.getTenantId()
                );

                syncMsgExtra = new InvCountExtra(
                        BaseConstants.Flag.YES,
                        Constants.InvCountExtra.PROGRAM_KEY_WMS_SYNC_ERR_MSG,
                        invCountHeader.getCountHeaderId(),
                        invCountHeader.getTenantId()
                );
            } else {
                Map<String, InvCountExtra> extraMap = extras
                        .stream()
                        .collect(Collectors.toMap(InvCountExtra::getProgramkey, Function.identity()));

                syncStatusExtra = extraMap.get(Constants.InvCountExtra.PROGRAM_KEY_WMS_SYNC_STATUS);
                syncMsgExtra = extraMap.get(Constants.InvCountExtra.PROGRAM_KEY_WMS_SYNC_ERR_MSG);
            }

            // list will always have either empty list or at most 1 warehouse
            InvWarehouse warehouse = warehouseFilterList.get(0);
            if (BaseConstants.Flag.YES.equals(warehouse.getIsWmsWarehouse())) {
                List<InvCountLineDTO> invCountLineDTOListFiltered = countLineDTOList
                        .stream()
                        .filter(line -> line.getCountHeaderId().equals(invCountHeader.getCountHeaderId()))
                        .collect(Collectors.toList());
                invCountHeader.setCountOrderLineList(invCountLineDTOListFiltered);

                ResponsePayloadDTO response = callWmsApiPushCountOrder(
                        Constants.ExternalService.NAMESPACE,
                        Constants.ExternalService.SERVER_CODE,
                        Constants.ExternalService.INTERFACE_CODE,
                        invCountHeader
                );

                if (!response.hasBody() || response.getBody() == null) {
                    throw new CommonException("error getting response body");
                }

                JSONObject responseBody = new JSONObject(response.getBody());

                if(responseBody.getString(Constants.ExternalService.RESULT_STATUS_FIELD).equals(Constants.ExternalService.RESULT_STATUS_SUCCESS)) {
                    syncStatusExtra.setProgramkey(Enums.Extra.WmsStatus.SUCCESS.name());
                    syncMsgExtra.setProgramvalue("");
                } else {
                    syncStatusExtra.setProgramkey(Enums.Extra.WmsStatus.ERROR.name());
                    String errMsg = responseBody.getString(Constants.ExternalService.RETURN_MESSAGE_FIELD);
                    syncMsgExtra.setProgramvalue(errMsg);
                    invCountHeader.setErrorMessage(errMsg);
                }

                invCountHeader.setRelatedWmsOrderCode(responseBody.getString(Constants.ExternalService.CODE_FIELD));

            } else {
                syncStatusExtra.setProgramkey(Enums.Extra.WmsStatus.SKIP.name());
            }

            invCountExtras.add(syncStatusExtra);
            invCountExtras.add(syncMsgExtra);
            if (!invCountHeader.getErrorMessage().isEmpty()) {
                invalidDTO.add(invCountHeader);
            } else {
                validDTO.add(invCountHeader);
            }
        }

        invCountExtraService.saveData(invCountExtras);

        countInfoDTO.setErrSize(invalidDTO.size());
        return countInfoDTO;
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO) {

        //Determine whether the warehouse is a WMS warehouse
        InvWarehouse warehouse = invWarehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId());
        if (BaseConstants.Flag.YES.equals(warehouse.getIsWmsWarehouse())) {
            String errorMsg = "The current warehouse is not a WMS warehouse, operations are not allowed";
            String status="E";
        }

        //Check data consistency
        InvCountLine lineRecord = new InvCountLine();
        lineRecord.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
        List<InvCountLine> countLinesDB = invCountLineRepository.selectList(lineRecord);
        List<Long> countLinesDBIds = countLinesDB
                .stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toList());
        List<Long> countLinesDTOIds = invCountHeaderDTO.getCountOrderLineList()
                .stream()
                .map(InvCountLine::getCountLineId)
                .collect(Collectors.toList());

        Set<Long> countLinesDBIdsSet = new HashSet<>(countLinesDBIds);
        Set<Long> countLinesDTOIdsSet = new HashSet<>(countLinesDTOIds);

        if (invCountHeaderDTO.getCounterList().size() != countLinesDB.size() || !countLinesDBIdsSet.equals(countLinesDTOIdsSet)) {
            String errorMsg = "The counting order line data is inconsistent with the INV system, please check the data";
            String status = "E";
        }

        //TODO: Update the line data
        invCountLineService.saveData(invCountHeaderDTO.getCountOrderLineList());
        return invCountHeaderDTO;
    }

    private ResponsePayloadDTO callWmsApiPushCountOrder(String namespace, String serverCode, String interfaceCode, InvCountHeaderDTO invCountHeaderDTO) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setPayload(JSON.toJSONString(invCountHeaderDTO));
        return interfaceInvokeSdk.invoke(namespace, serverCode, interfaceCode, requestPayloadDTO);
    }

    /**
     * generate invoice header number
     * @return invoice header number with format
     */
    private String generateCountNumber() {
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put(Constants.CodeBuilder.FIELD_CUSTOM_SEGMENT, String.valueOf(BaseConstants.DEFAULT_TENANT_ID));
        return codeRuleBuilder.generateCode(Constants.InvCountHeader.CODE_RULE, variableMap);
    }

    private List<String> getValidCountStatus() {
        return lovAdapter.queryLovValue(Constants.InvCountHeader.STATUS_LOV_CODE,BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    private UserInfoDTO createNewUserInfoDTO(Long id) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(id);
        return dto;
    }

    private List<UserInfoDTO> convertUserIdToList(String userIds) {
        return Arrays.stream(userIds.split(","))
                .map(id -> createNewUserInfoDTO(Long.valueOf(id)))
                .collect(Collectors.toList());
    }

    private List<String> getLovValues(String lovCode, Long tenantId) {
        return lovAdapter.queryLovValue(lovCode, tenantId)
                .stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());
    }

    /**
     * filter list of inventory count headers that has specified count status
     * @param invCountHeaders list of inventory count headers
     * @param status status to find
     * @return inventory count header containing status from parameter
     */
    private List<InvCountHeader> filterHeaderListByStatus(List<InvCountHeader> invCountHeaders, String status) {
        return invCountHeaders
                .stream()
                .filter(header -> status.equals(header.getCountStatus()))
                .collect(Collectors.toList());
    }

    private List<InvCountLineDTO> generateCountLinesFromStocks(InvCountHeaderDTO header, List<InvStock> stocks) {
        List<InvStock> headerStocks = stocks.stream().filter(stock -> {
            List<Long> batchIds = Utils.convertStringIdstoList(header.getSnapshotBatchIds());
            List<Long> materialIds = Utils.convertStringIdstoList(header.getSnapshotMaterialIds());

            boolean company = stock.getCompanyId().equals(header.getCompanyId());
            boolean department = stock.getDepartmentId().equals(header.getDepartmentId());
            boolean warehouse = stock.getWarehouseId().equals(header.getWarehouseId());
            boolean batch = batchIds.contains(stock.getBatchId());
            boolean material = materialIds.contains(stock.getMaterialId());

            return company && department && warehouse && batch && material;
        }).collect(Collectors.toList());

        return transferDataToCountOrderLine(header, headerStocks);
    }

    private List<InvCountLineDTO> transferDataToCountOrderLine(InvCountHeaderDTO header, List<InvStock> headerStocks) {
        List<InvCountLineDTO> invCountLines = new ArrayList<>();

        for (int i = 0; i < headerStocks.size(); i++) {
            InvStock stock = headerStocks.get(i);
            InvCountLineDTO dto = new InvCountLineDTO();

            dto.setCountHeaderId(header.getCountHeaderId());
            dto.setCounterIds(header.getCounterIds());
            dto.setBatchId(stock.getBatchId());
            dto.setMaterialId(stock.getMaterialId());
            dto.setWarehouseId(stock.getWarehouseId());
            dto.setLineNumber(i+1);
            dto.setUnitCode(stock.getUnitCode());
            dto.setSnapshotUnitQty(stock.getUnitQuantity());
            dto.setTenantId(header.getTenantId());

            invCountLines.add(dto);
        }
        return invCountLines;
    }

}

