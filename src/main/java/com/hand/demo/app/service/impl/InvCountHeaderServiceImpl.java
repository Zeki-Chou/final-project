package com.hand.demo.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.enums.HeaderStatus;
import com.hand.demo.infra.enums.WmsStatus;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.RequiredArgsConstructor;
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
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author Allan
 * @since 2024-11-25 08:19:18
 */
@Service
@RequiredArgsConstructor
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
    private final ProfileClient profileClient;
    private final WorkflowClient workflowClient;

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {

        Page<InvCountHeaderDTO> countHeaderDTOS = PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
        Map<Long, List<InvCountLineDTO>> lineMap = findCountLines(countHeaderDTOS);
        countHeaderDTOS.forEach(header -> {
            List<InvCountLineDTO> lineDTOList = lineMap.getOrDefault(header.getCountHeaderId(), new ArrayList<>());
            header.setCountOrderLineList(lineDTOList);
        });

        return countHeaderDTOS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        insertList.forEach(header -> {
            header.setCountStatus(HeaderStatus.DRAFT.name());
            header.setCountNumber(generateCountNumber());
            header.setDelFlag(BaseConstants.Flag.NO);
        });

        List<InvCountHeader> draftUpdateList = filterHeaderListByStatus(updateList, HeaderStatus.DRAFT.name());
        List<InvCountHeader> inCountingUpdateList = filterHeaderListByStatus(updateList, HeaderStatus.INCOUNTING.name());
        List<InvCountHeader> rejectedUpdateList = filterHeaderListByStatus(updateList, HeaderStatus.REJECTED.name());

        List<InvCountHeaderDTO> draftInsertRes = invCountHeaderRepository
                .batchInsertSelective(insertList)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        // document with counting status can only update remark and reason
        List<InvCountHeaderDTO> inCountingUpdateRes = invCountHeaderRepository
                .batchUpdateOptional(inCountingUpdateList, InvCountHeader.FIELD_REMARK, InvCountHeader.FIELD_REASON)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        // rejected status can only update reason
        List<InvCountHeaderDTO> rejectedUpdateRes = invCountHeaderRepository
                .batchUpdateOptional(rejectedUpdateList, InvCountHeader.FIELD_REASON)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        // draft aren't allowed to change both remark and reason as it still in initialization process
        draftUpdateList.forEach(header -> {
            header.setRemark(null);
            header.setReason(null);
        });

        List<InvCountHeaderDTO> draftUpdateRes = invCountHeaderRepository
                .batchUpdateByPrimaryKeySelective(draftUpdateList)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        List<InvCountHeaderDTO> resultDTO = new ArrayList<>();
        resultDTO.addAll(draftInsertRes);
        resultDTO.addAll(draftUpdateRes);
        resultDTO.addAll(inCountingUpdateRes);
        resultDTO.addAll(rejectedUpdateRes);

        return resultDTO;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validHeaderDTO = new ArrayList<>();
        List<InvCountHeaderDTO> invalidHeaderDTO = new ArrayList<>();

        List<InvCountHeaderDTO> headersDB = findHeadersDBFromInput(invCountHeaderDTOS);

        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long currentUser = iamJSONObject.getLong(Constants.Iam.FIELD_ID);

        headersDB.forEach(header -> {
            if (!header.getCountStatus().equals(HeaderStatus.DRAFT.name())) {
                header.setErrorMessage("Only allow draft status to be deleted");
                invalidHeaderDTO.add(header);
            } else if (!currentUser.equals(header.getCreatedBy())) {
                header.setErrorMessage("Only current user is document creator allow delete document");
                invalidHeaderDTO.add(header);
            } else {
                header.setErrorMessage("");
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
        InvCountHeaderDTO header = invCountHeaderRepository.selectByPrimary(countHeaderId);

        if (header == null) {
            throw new CommonException("header not found");
        }

        if (wmsWarehouseIds.contains(header.getWarehouseId())) {
            header.setIsWmsWarehouse(BaseConstants.Flag.YES);
        } else {
            header.setIsWmsWarehouse(BaseConstants.Flag.NO);
        }

        List<UserInfoDTO> counterList = convertUserIdToList(header.getCounterIds());
        List<UserInfoDTO> supervisorList = convertUserIdToList(header.getSupervisorIds());
        List<MaterialInfoDTO> materialInfoDTOList = invMaterialService.convertMaterialIdsToList(header.getSnapshotMaterialIds());
        List<BatchInfoDTO> batchInfoDTOList = invBatchService.convertBatchIdsToList(header.getSnapshotBatchIds());

        header.setCounterList(counterList);
        header.setSupervisorList(supervisorList);
        header.setSnapshotBatchList(batchInfoDTOList);
        header.setSnapshotMaterialList(materialInfoDTOList);
        return header;
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
        List<InvCountHeaderDTO> headersDB = findHeadersDBFromInput(invCountHeaders);
        //company
        List<Long> companyIdList = headersDB.stream().map(InvCountHeader::getCompanyId).collect(Collectors.toList());
        List<Long> companiesIds = iamCompanyService.findCompanyIds(companyIdList);

        //warehouse
        List<Long> warehouseIdList = headersDB.stream().map(InvCountHeader::getWarehouseId).collect(Collectors.toList());
        List<Long> warehousesIds = invWarehouseService.findByIds(warehouseIdList);

        //department
        List<Long> departmentIdList = headersDB.stream().map(InvCountHeader::getDepartmentId).collect(Collectors.toList());
        List<Long> departmentsIds = iamDepartmentService.findByIds(departmentIdList);

        for (InvCountHeaderDTO header: headersDB) {
            header.setErrorMessage("");
            InvStockDTO invStockDTO = new InvStockDTO();
            invStockDTO.setCompanyId(header.getCompanyId());
            invStockDTO.setDepartmentId(header.getDepartmentId());
            invStockDTO.setWarehouseId(header.getWarehouseId());
            invStockDTO.setMaterialIds(Utils.convertStringIdstoList(header.getSnapshotMaterialIds()));

            //TODO format count time string

            // Query on hand quantity based on the
            // tenant+company+department+warehouse+material+on hand quantity not equals 0
            List<BigDecimal> onHandQuantities = invStockRepository.getSumOnHandQty(invStockDTO);

            if (!header.getCountStatus().equals(HeaderStatus.DRAFT.name())) {
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
            } else if (onHandQuantities.isEmpty()) {
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
        invCountHeaderList.forEach(header -> header.setCountStatus(HeaderStatus.INCOUNTING.name()));
        List<InvCountHeader> headers = invCountHeaderList
                .stream()
                .filter(header -> header.getCountHeaderId() != null)
                .collect(Collectors.toList());

        // update header
        List<InvCountHeader> updateResult = invCountHeaderRepository.batchUpdateByPrimaryKeySelective(headers);

        //for each header, create list of lines using stock data
        List<InvStock> stocks = invStockRepository.selectStocksByHeaders(updateResult);
        List<InvCountLineDTO> countLinesToInsert = new ArrayList<>();
        updateResult.forEach(header -> {
            List<InvCountLineDTO> invCountLines = this.generateCountLinesFromStocks(header, stocks);
            countLinesToInsert.addAll(invCountLines);
        });

        invCountLineService.saveData(countLinesToInsert);
        return updateResult.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {

        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validHeaderDTOS = invCountHeaderDTOS
                .stream()
                .filter(header -> header.getCountHeaderId() == null)
                .collect(Collectors.toList());
        List<InvCountHeaderDTO> invalidHeaderDTOS = new ArrayList<>();

        List<InvCountHeaderDTO> updateList = invCountHeaderDTOS
                .stream()
                .filter(header -> header.getCountHeaderId() != null)
                .collect(Collectors.toList());

        List<Long> warehouseWMSIds = invWarehouseService.getWMSWarehouseIds();
        List<String> validUpdateStatuses = getLovValues(Constants.InvCountHeader.STATUS_LOV_CODE,BaseConstants.DEFAULT_TENANT_ID);

        String draftValue = HeaderStatus.DRAFT.name();
        List<String> validManualUpdateStatus = HeaderStatus.validManualUpdateStatus();

        JSONObject iamJSONObject = Utils.getIamJSONObject(iamRemoteService);
        Long userId = iamJSONObject.getLong(Constants.Iam.FIELD_ID);

        // business verifications for update data
        for (InvCountHeaderDTO invCountHeaderDTO: updateList) {
            invCountHeaderDTO.setErrorMessage("");

            List<Long> headerCounterIds = Utils.convertStringIdstoList(invCountHeaderDTO.getCounterIds());
            List<Long> supervisorIds = Utils.convertStringIdstoList(invCountHeaderDTO.getSupervisorIds());
            List<Long> headerAndSupervisorIds = new ArrayList<>();
            headerAndSupervisorIds.addAll(headerCounterIds);
            headerAndSupervisorIds.addAll(supervisorIds);

            String headerStatus = invCountHeaderDTO.getCountStatus();
            Long headerWarehouseId = invCountHeaderDTO.getWarehouseId();
            Long documentCreatorId = invCountHeaderDTO.getCreatedBy();

            if (!validUpdateStatuses.contains(headerStatus)) {
                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_STATUS_INVALID);
            } else if (draftValue.equals(headerStatus) && !userId.equals(documentCreatorId)) {
                invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.UPDATE_ACCESS_INVALID);
            } else if (validManualUpdateStatus.contains(headerStatus)) {
                if (warehouseWMSIds.contains(headerWarehouseId) && !supervisorIds.contains(userId)) {
                    invCountHeaderDTO.setErrorMessage(Constants.InvCountHeader.WAREHOUSE_SUPERVISOR_INVALID);
                } else if (headerAndSupervisorIds.contains(userId) && !userId.equals(documentCreatorId)) {
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
    @Transactional(rollbackFor = Exception.class)
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList) {

        InvCountInfoDTO countInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validDTO = new ArrayList<>();
        List<InvCountHeaderDTO> invalidDTO = new ArrayList<>();

        //Query warehouse data based on warehouseCode
        List<Long> warehouseIds = invCountHeaderList
                .stream()
                .map(InvCountHeaderDTO::getWarehouseId)
                .distinct()
                .collect(Collectors.toList());

        List<InvWarehouse> warehouses = invWarehouseRepository.selectByIds(Utils.generateStringIds(warehouseIds));
        List<Long> headerIds = invCountHeaderList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        List<InvCountLineDTO> countLineDTOList = invCountLineRepository.selectByCountHeaderIds(headerIds);

        // for syncStatus and syncMessage to be updated/inserted
        List<InvCountExtra> invCountExtras = new ArrayList<>();
        // for update wms related order
        List<InvCountHeader> invCountHeaders = new ArrayList<>();

        for (InvCountHeaderDTO invCountHeader: invCountHeaderList) {
            invCountHeader.setErrorMessage("");
            Optional<InvWarehouse> wareHouse = warehouses
                                                    .stream()
                                                    .filter(warehouse -> warehouse.getWarehouseId().equals(invCountHeader.getWarehouseId()) &&
                                                            warehouse.getTenantId().equals(invCountHeader.getTenantId()))
                                                    .findFirst();

            InvCountExtra extraRecord = new InvCountExtra();
            extraRecord.setSourceid(invCountHeader.getCountHeaderId());
            extraRecord.setEnabledflag(BaseConstants.Flag.YES);
            List<InvCountExtra> extras = invCountExtraRepository.selectList(extraRecord);

            InvCountExtra syncStatusExtra = invCountExtraService.getExtraOrInitialize(extras, invCountHeader, Constants.InvCountExtra.PROGRAM_KEY_WMS_SYNC_STATUS);
            InvCountExtra syncMsgExtra = invCountExtraService.getExtraOrInitialize(extras, invCountHeader, Constants.InvCountExtra.PROGRAM_KEY_WMS_SYNC_ERR_MSG);

            if (!wareHouse.isPresent()) {
                invCountHeader.setErrorMessage("warehouse not found");
            } else if (BaseConstants.Flag.YES.equals(wareHouse.get().getIsWmsWarehouse())) {
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

                JSONObject responseBody = new JSONObject(response.getPayload());

                if(responseBody.getString(Constants.ExternalService.RESULT_STATUS_FIELD).equals(Constants.ExternalService.RESULT_STATUS_SUCCESS)) {
                    syncStatusExtra.setProgramkey(WmsStatus.SUCCESS.name());
                    syncMsgExtra.setProgramvalue("");
                    invCountHeader.setRelatedWmsOrderCode(responseBody.getString(Constants.ExternalService.CODE_FIELD));
                    invCountHeaders.add(invCountHeader);
                } else {
                    syncStatusExtra.setProgramvalue(WmsStatus.ERROR.name());
                    String errMsg = responseBody.getString(Constants.ExternalService.RETURN_MESSAGE_FIELD);
                    syncMsgExtra.setProgramvalue(errMsg);
                    invCountHeader.setErrorMessage(errMsg);
                }

            } else {
                syncStatusExtra.setProgramvalue(WmsStatus.SKIP.name());
                syncMsgExtra.setProgramvalue("");
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
        if (!invCountHeaders.isEmpty()) {
            // only update since the input is from the execute method
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(invCountHeaders);
        }
        countInfoDTO.setValidHeaderDTOS(validDTO);
        countInfoDTO.setInvalidHeaderDTOS(invalidDTO);
        countInfoDTO.setErrSize(invalidDTO.size());
        return countInfoDTO;
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO) {

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

        //Determine whether the warehouse is a WMS warehouse
        InvWarehouse warehouse = invWarehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId());
        if (BaseConstants.Flag.NO.equals(warehouse.getIsWmsWarehouse())) {
            invCountHeaderDTO.setErrorMessage(Constants.ExternalService.ERR_INVALID_WMS);
            invCountHeaderDTO.setStatus(Constants.ExternalService.RESULT_STATUS_ERROR);
        } else if (invCountHeaderDTO.getCounterList().size() != countLinesDB.size() || !countLinesDBIdsSet.equals(countLinesDTOIdsSet)) {
            invCountHeaderDTO.setErrorMessage(Constants.ExternalService.ERR_COUNT_LINE_INCONSISTENT);
            invCountHeaderDTO.setStatus(Constants.ExternalService.RESULT_STATUS_ERROR);
        } else {
            invCountLineService.saveData(invCountHeaderDTO.getCountOrderLineList());
            invCountHeaderDTO.setStatus(Constants.ExternalService.RESULT_STATUS_SUCCESS);
        }

        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> countHeaders) {

        InvCountInfoDTO countInfoDTO = new InvCountInfoDTO();
        List<InvCountHeaderDTO> validDTO = new ArrayList<>();
        List<InvCountHeaderDTO> invalidDTO = new ArrayList<>();

        List<Long> countHeaderIdList = countHeaders.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        List<InvCountHeader> headersDB = invCountHeaderRepository.selectByIds(Utils.generateStringIds(countHeaderIdList));
        List<InvCountHeaderDTO> headersDTO = headersDB.stream().map(this::mapToDTO).collect(Collectors.toList());
        Map<Long, List<InvCountLineDTO>> lineMap = findCountLines(headersDTO);

        List<String> validSubmitStatus = HeaderStatus.validSubmitStatus();

        CustomUserDetails customUserDetails = DetailsHelper.getUserDetails();

        // Determine whether there is data with null value unit quantity field
        // When exists, verification failed, prompt: "There are data rows with empty count quantity. Please check the data."
        // When not exists, verification passed
        // When there is a difference in counting, the reason field must be entered.

        for (InvCountHeaderDTO header: headersDTO) {
            header.setErrorMessage("");
            List<Long> supervisorIds = Utils.convertStringIdstoList(header.getSupervisorIds());

            if (!validSubmitStatus.contains(header.getCountStatus())) {
                header.setErrorMessage(Constants.InvCountHeader.SUBMIT_STATUS_INVALID);
            } else if (!supervisorIds.contains(customUserDetails.getUserId()) ) {
                header.setErrorMessage(Constants.InvCountHeader.SUBMIT_USER_INVALID);
            } else {
                lineMap.get(header.getCountHeaderId()).forEach(line -> {
                    if (line.getUnitQty() == null) {
                        header.setErrorMessage(Constants.InvCountHeader.SUBMIT_COUNT_QTY_INVALID);
                    } else if (!BigDecimal.ZERO.equals(line.getUnitDiffQty()) && header.getReason() == null) {
                        header.setErrorMessage(Constants.InvCountHeader.SUBMIT_REASON_INVALID);
                    }
                });
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
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invoiceHeaders) {
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();
        // Get configuration file value
        String workflowFlag = profileClient.getProfileValueByOptions(
                userDetails.getTenantId(),
                userDetails.getUserId(),
                userDetails.getRoleId(),
                Constants.InvCountHeader.COUNTING_WORKFLOW
        );

        List<InvCountHeader> updateList = new ArrayList<>();

        // Determine whether to start workflow
        for (InvCountHeaderDTO header: invoiceHeaders) {
            Map<String, Object> variableMap = new HashMap<>();
            variableMap.put(Constants.Workflow.DEPARTMENT_FIELD, header.getDepartmentId());

            if (!workflowFlag.isEmpty() && Integer.valueOf(workflowFlag).equals(BaseConstants.Flag.YES)) {
                workflowClient.startInstanceByFlowKey(
                        BaseConstants.DEFAULT_TENANT_ID,
                        Constants.Workflow.FLOW_KEY,
                        header.getCountNumber(),
                        Constants.Workflow.DIMENSION,
                        Constants.Workflow.STARTER,
                        variableMap
                );
            } else {
                // update document status to CONFIRMED
                header.setStatus(HeaderStatus.CONFIRMED.name());
                updateList.add(header);
            }
        }

        List<InvCountHeader> updateRes = invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
        return updateRes.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public InvCountHeaderDTO updateApprovalCallback(WorkFlowEventDTO workFlowEventDTO) {
        InvCountHeaderDTO headerRecord = new InvCountHeaderDTO();
        headerRecord.setCountNumber(workFlowEventDTO.getBusinessKey());
        List<InvCountHeaderDTO> countHeaderList = invCountHeaderRepository.selectList(headerRecord);

        // don't need if guard because of submitCheck
        InvCountHeaderDTO dto = countHeaderList.get(0);
        String status = workFlowEventDTO.getDocStatus();
        dto.setCountStatus(status);
        if (HeaderStatus.APPROVED.name().equals(status)) {
            dto.setApprovedTime(workFlowEventDTO.getApprovedTime());
        }

        int updated = invCountHeaderRepository.updateByPrimaryKeySelective(dto);
        if (updated == 1) {
            return dto;
        } else {
            return null;
        }
    }

    @Override
    public void withdrawWorkflow(Long organizationId, WorkFlowEventDTO dto) {
        workflowClient.flowWithdrawFlowKey(organizationId, Constants.Workflow.FLOW_KEY, dto.getBusinessKey());
    }

    private Map<Long, List<InvCountLineDTO>> findCountLines(List<InvCountHeaderDTO> headers) {
        List<Long> headerIds = headers.stream().map(InvCountHeader::getCountHeaderId).collect(Collectors.toList());
        return invCountLineRepository.selectByCountHeaderIds(headerIds)
                .stream()
                .collect(Collectors.groupingBy(InvCountLine::getCountHeaderId));
    }

    private ResponsePayloadDTO callWmsApiPushCountOrder(String namespace, String serverCode, String interfaceCode, InvCountHeaderDTO invCountHeaderDTO) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();

        Map<String, String> headerParamsMap = new HashMap<>();
        // TODO: change getToken to TokenUtils.getToken and implement it
        headerParamsMap.put("Authorization", "Bearer " + invCountHeaderDTO.getToken());

        requestPayloadDTO.setPayload(JSON.toJSONString(invCountHeaderDTO));
        requestPayloadDTO.setHeaderParamMap(headerParamsMap);
        requestPayloadDTO.setMediaType("application/json");
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

    private List<InvCountLineDTO> generateCountLinesFromStocks(InvCountHeader header, List<InvStock> stocks) {
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

    private List<InvCountHeaderDTO> findHeadersDBFromInput(List<InvCountHeaderDTO> inputDTOList) {
        List<Long> headerIds = inputDTOList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        List<InvCountHeader> headersDB = invCountHeaderRepository.selectByIds(Utils.generateStringIds(headerIds));
        return headersDB.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private InvCountHeaderDTO mapToDTO(InvCountHeader header) {
        InvCountHeaderDTO dto = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, dto);
        return dto;
    }

    private List<InvCountLineDTO> transferDataToCountOrderLine(InvCountHeader header, List<InvStock> headerStocks) {
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
            dto.setCounterIds(header.getCounterIds());

            invCountLines.add(dto);
        }
        return invCountLines;
    }

}

