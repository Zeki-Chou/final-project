package com.hand.demo.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hand.demo.api.dto.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountExtraConstants;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import com.hand.demo.infra.constant.InvCountLineConstants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.seata.common.util.StringUtils;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 09:59:38
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;
    @Autowired
    private InvCountLineRepository invCountLineRepository;
    @Autowired
    private CodeRuleBuilder codeRuleBuilder;
    @Autowired
    private LovAdapter lovAdapter;
    @Autowired
    private IamCompanyRepository iamCompanyRepository;
    @Autowired
    private IamDepartmentRepository iamDepartmentRepository;
    @Autowired
    private InvWarehouseRepository warehouseRepository;
    @Autowired
    private InvStockRepository invStockRepository;
    @Autowired
    private InvWarehouseRepository invWarehouseRepository;
    @Autowired
    private InvCountExtraRepository invCountExtraRepository;
    @Autowired
    private InterfaceInvokeSdk interfaceInvokeSdk;
    @Autowired
    private ProfileClient profileClient;
    @Autowired
    private WorkflowClient workflowClient;


    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        // get header database data
        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = new HashMap<>();
        String oldHeaderIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).map(headerDTO -> headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(","));
        if (!oldHeaderIds.isEmpty()) {
            List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(oldHeaderIds);
            oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        }
        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            String errorMsg = null;
            try {
                // skip insert headers
                if (invCountHeaderDTO.getCountHeaderId() == null) {
                    continue;
                }

                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());

                // not allow update status field
                if (!oldInvCountHeaderDTO.getCountStatus().equals(invCountHeaderDTO.getCountStatus())) {
                    throw new CommonException("Not allow update status field");
                }

                Long currentUserId = DetailsHelper.getUserDetails().getUserId();
                if (oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.DRAFT)) {
                    // Document in draft status can only be modified by the document creator.
                    if (!oldInvCountHeaderDTO.getCreatedBy().equals(currentUserId)) {
                        throw new CommonException("Document in draft status can only be modified by the document creator.");
                    }
                } else if (oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.IN_COUNTING)
                        || oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.REJECTED)
                        || oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.WITHDRAWN)) {
                    // The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.
                    if (oldInvCountHeaderDTO.getIsWMSWarehouse() && !Arrays.asList(oldInvCountHeaderDTO.getSupervisorIds().split(",")).contains(currentUserId.toString())) {
                        throw new CommonException("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
                    }
                    // Only the document creator, counter, and supervisor can modify the document for the status of in counting, rejected, withdrawn.
                    if (!oldInvCountHeaderDTO.getCreatedBy().equals(currentUserId)
                            && !Arrays.asList(oldInvCountHeaderDTO.getCounterIds().split(",")).contains(currentUserId.toString())
                            && !Arrays.asList(oldInvCountHeaderDTO.getSupervisorIds().split(",")).contains(currentUserId.toString())) {
                        throw new CommonException("Only the document creator, counter, and supervisor can modify the document for the status of in counting, rejected, withdrawn.");
                    }
                } else {
                    // Only draft, in counting, rejected, and withdrawn status can be modified
                    throw new CommonException("Only draft, in counting, rejected, and withdrawn status can be modified");
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            // populate info data
            if (StringUtils.isNotBlank(errorMsg)) {
                invCountHeaderDTO.setErrorMsg(errorMsg);
                invCountInfoDTO.getErrorList().add(invCountHeaderDTO);
            } else {
                invCountInfoDTO.getSuccessList().add(invCountHeaderDTO);
            }
            invCountInfoDTO.setCompleteErrMsg(invCountInfoDTO.getErrorList().stream().map(InvCountHeaderDTO::getErrorMsg).collect(Collectors.joining(",")));
        }
        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        // get header database data
        String oldHeaderIds = invCountHeaderDTOS.stream()
                .filter(headerDTO -> headerDTO.getCountHeaderId() != null)
                .map(headerDTO -> headerDTO.getCountHeaderId().toString())
                .collect(Collectors.joining(","));
        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = new HashMap<>();
        if (!oldHeaderIds.isEmpty()) {
            List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(oldHeaderIds);
            oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        }

        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());

                // status verification, Only allow draft status to be deleted
                if (!oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.DRAFT)) {
                    throw new CommonException("Only allow draft status to be deleted");
                }

                // current user verification, Only current user is document creator allow delete document
                if (!oldInvCountHeaderDTO.getCreatedBy().equals(DetailsHelper.getUserDetails().getUserId())) {
                    throw new CommonException("Only current user is document creator allow delete document");
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            // populate info data
            if (StringUtils.isNotBlank(errorMsg)) {
                invCountHeaderDTO.setErrorMsg(errorMsg);
                invCountInfoDTO.getErrorList().add(invCountHeaderDTO);
            } else {
                invCountInfoDTO.getSuccessList().add(invCountHeaderDTO);
            }
        }
        invCountInfoDTO.setCompleteErrMsg(invCountInfoDTO.getErrorList().stream().map(InvCountHeaderDTO::getErrorMsg).collect(Collectors.joining(",")));

        if(invCountInfoDTO.getErrorList().isEmpty()){
            // remove
            invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaderDTOS);
        }

        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS){
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        // get header db data
        String oldHeaderIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).map(headerDTO -> headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(","));
        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = new HashMap<>();
        if (!oldHeaderIds.isEmpty()) {
            List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(oldHeaderIds);
            oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        }

        // get company, department, & warehouse db data
        Set<String> oldCompanySet = new HashSet<>();
        Set<String> oldDepartmentSet = new HashSet<>();
        Set<String> oldWarehouseSet = new HashSet<>();
        String oldCompanyIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCompanyId() != null).map(headerDTO -> headerDTO.getCompanyId().toString()).collect(Collectors.joining(","));
        String oldDepartmentIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getDepartmentId() != null).map(headerDTO -> headerDTO.getDepartmentId().toString()).collect(Collectors.joining(","));
        String oldWarehouseIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getWarehouseId() != null).map(headerDTO -> headerDTO.getWarehouseId().toString()).collect(Collectors.joining(","));
        if(!oldCompanyIds.isEmpty()){
            oldCompanySet = iamCompanyRepository.selectByIds(oldCompanyIds).stream().map(company->company.getCompanyId().toString()).collect(Collectors.toSet());
        }
        if(!oldDepartmentIds.isEmpty()){
            oldDepartmentSet = iamDepartmentRepository.selectByIds(oldDepartmentIds).stream().map(department->department.getDepartmentId().toString()).collect(Collectors.toSet());
        }
        if(!oldWarehouseIds.isEmpty()){
            oldWarehouseSet= warehouseRepository.selectByIds(oldWarehouseIds).stream().map(warehouse->warehouse.getWarehouseId().toString()).collect(Collectors.toSet());
        }
        List<String> statusLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.Lov.Status.CODE, DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> dimensionLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.Lov.Dimension.CODE, DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> typeLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.Lov.Type.CODE, DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());
        List<String> modeLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.Lov.Mode.CODE, DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());

        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());

                // a. document status validation: Only draft status can execute
                if (!oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.DRAFT)) {
                    throw new CommonException("Only draft status can execute");
                }

                // b. current login user validation: Only the document creator can execute
                if (!oldInvCountHeaderDTO.getCreatedBy().equals(DetailsHelper.getUserDetails().getUserId())) {
                    throw new CommonException("Only the document creator can execute");
                }

                // c. value set validation

                if (!statusLovValueDTOS.contains(invCountHeaderDTO.getCountStatus())) {
                    throw new CommonException("Invalid status value");
                }
                if (!dimensionLovValueDTOS.contains(invCountHeaderDTO.getCountDimension())) {
                    throw new CommonException("Invalid dimension value");
                }
                if (!typeLovValueDTOS.contains(invCountHeaderDTO.getCountType())) {
                    throw new CommonException("Invalid type value");
                }
                if (!modeLovValueDTOS.contains(invCountHeaderDTO.getCountMode())) {
                    throw new CommonException("Invalid mode value");
                }

                if (invCountHeaderDTO.getCountType().equals(InvCountHeaderConstants.Value.CountType.MONTH)) {
                    YearMonth.parse(invCountHeaderDTO.getCountTimeStr(), DateTimeFormatter.ofPattern("yyyy-MM"));
                } else {
                    Year.parse(invCountHeaderDTO.getCountTimeStr(), DateTimeFormatter.ofPattern("yyyy"));
                }

                // d. company, department, warehouse validation
                if (!oldCompanySet.contains(invCountHeaderDTO.getCompanyId().toString())) {
                    throw new CommonException("Company id not exist");
                }
                if (!oldDepartmentSet.contains(invCountHeaderDTO.getDepartmentId().toString())) {
                    throw new CommonException("Department id not exist");
                }
                if (!oldWarehouseSet.contains(invCountHeaderDTO.getWarehouseId().toString())) {
                    throw new CommonException("Warehouse id not exist");
                }

                // e. on hand quantity validation Query on hand quantity based on the tenant+company+department+warehouse+material+on hand quantity not equals 0, if no data is queried, error message: Unable to query on hand quantity data.
                if (!invStockRepository.checkByHeader(invCountHeaderDTO)) {
                    throw new CommonException("Unable to query on hand quantity data.");
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            // populate info data
            if (StringUtils.isNotBlank(errorMsg)) {
                invCountHeaderDTO.setErrorMsg(errorMsg);
                invCountInfoDTO.getErrorList().add(invCountHeaderDTO);
            } else {
                invCountInfoDTO.getSuccessList().add(invCountHeaderDTO);
            }
        }
        invCountInfoDTO.setCompleteErrMsg(invCountInfoDTO.getErrorList().stream().map(InvCountHeaderDTO::getErrorMsg).collect(Collectors.joining(",")));

        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO countSyncWMS(List<InvCountHeaderDTO> invCountHeaderDTOS){
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        // get header db
        InvCountHeaderDTO headerListParam = new InvCountHeaderDTO();
        headerListParam.setIds(invCountHeaderDTOS.stream().map(headerDTO->headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(",")));
        List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectList(headerListParam);
        Map<String,InvCountHeaderDTO> oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);

        // get warehouse db
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectByIds(invCountHeaderDTOS.stream().map(headerDTO ->headerDTO.getWarehouseId().toString()).collect(Collectors.joining(",")));
        Map<String,InvWarehouse> invWarehouseMap = convertInvWarehouseToMap(invWarehouses);

        List<InvCountExtra> invCountExtras = new ArrayList<>();
        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());
                Utils.populateNullFields(oldInvCountHeaderDTO, invCountHeaderDTO);
                invCountHeaderDTO.setObjectVersionNumber(oldInvCountHeaderDTO.getObjectVersionNumber());

                // a. Determine whether the warehouse data exists by tenantId and warehouseCode
                InvWarehouse invWarehouse = invWarehouseMap.getOrDefault(invCountHeaderDTO.getWarehouseId().toString(), null);
                if (invWarehouse == null || !invWarehouse.getTenantId().equals(invCountHeaderDTO.getTenantId())) {
                    throw new CommonException("Can's find warehouse with id and tenantId specified");
                }

                // b. Get the extended table data based on the counting header ID extraRepository.select(sourceId=countHeaderId and enabledFlag=1); if can not get the result, need to initialize
                List<InvCountExtra> oldInvCountExtras = invCountExtraRepository.selectByHeader(invCountHeaderDTO);
                InvCountExtra syncStatusExtra = oldInvCountExtras.stream().filter(extra -> extra.getProgramkey().equals(InvCountExtraConstants.Value.ProgramKey.STATUS)).findFirst().orElse(newExtra(invCountHeaderDTO, InvCountExtraConstants.Value.ProgramKey.STATUS));
                InvCountExtra syncMsgExtra = oldInvCountExtras.stream().filter(extra -> extra.getProgramkey().equals(InvCountExtraConstants.Value.ProgramKey.ERROR_MESSAGE)).findFirst().orElse(newExtra(invCountHeaderDTO, InvCountExtraConstants.Value.ProgramKey.ERROR_MESSAGE));
                invCountExtras.add(syncStatusExtra);
                invCountExtras.add(syncMsgExtra);

                // c. Determine whether it is a WMS warehouse and call the WMS interface to synchronize the counting order
                if (invWarehouse.getIsWmsWarehouse() == 1) {
                    // call external interface
                    invCountHeaderDTO.setEmployeeNumber(DetailsHelper.getUserDetails().getUsername());
                    Map<String, String> requestHeaderMap = new HashMap<>();
                    requestHeaderMap.put(InvCountHeaderConstants.InterfaceSDK.WMSCounting.RequestHeader.KEY_AUTHORIZATION, InvCountHeaderConstants.InterfaceSDK.WMSCounting.RequestHeader.VALUE_AUTHORIZATION);
                    requestHeaderMap.put(InvCountHeaderConstants.InterfaceSDK.WMSCounting.RequestHeader.KEY_CONTENT_TYPE, InvCountHeaderConstants.InterfaceSDK.WMSCounting.RequestHeader.VALUE_CONTENT_TYPE);
                    requestHeaderMap.put(InvCountHeaderConstants.InterfaceSDK.WMSCounting.RequestHeader.KEY_ORGANIZATION_ID, invCountHeaderDTO.getTenantId().toString());

                    String headerJsonString = JSON.toJSONString(invCountHeaderDTO);
                    JSONObject requestPayloadJSON = JSON.parseObject(headerJsonString);
                    requestPayloadJSON.put("countOrderLineList", invCountHeaderDTO.getInvCountLineDTOList());

                    RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
                    requestPayloadDTO.setHeaderParamMap(requestHeaderMap);
                    requestPayloadDTO.setPayload(requestPayloadJSON.toJSONString());
                    requestPayloadDTO.setMediaType(InvCountHeaderConstants.InterfaceSDK.WMSCounting.RequestHeader.VALUE_CONTENT_TYPE);

                    ResponsePayloadDTO responsePayloadDTO = interfaceInvokeSdk.invoke(InvCountHeaderConstants.InterfaceSDK.WMSCounting.NAMESPACE, InvCountHeaderConstants.InterfaceSDK.WMSCounting.SERVER_CODE, InvCountHeaderConstants.InterfaceSDK.WMSCounting.INTERFACE_CODE, requestPayloadDTO);

                    // set extra based on response
                    JSONObject responsePayloadJSON = JSON.parseObject(responsePayloadDTO.getPayload());
                    if (responsePayloadJSON.getString(InvCountHeaderConstants.InterfaceSDK.WMSCounting.ResponseHeader.KEY_STATUS).equals(InvCountHeaderConstants.InterfaceSDK.WMSCounting.ResponseHeader.VALUE_STATUS_SUCCESS)) {
                        syncStatusExtra.setProgramvalue(InvCountExtraConstants.Value.ProgramValue.SUCCESS);
                        syncMsgExtra.setProgramvalue("");
                        invCountHeaderDTO.setRelatedWmsOrderCode(responsePayloadJSON.getString(InvCountHeaderConstants.InterfaceSDK.WMSCounting.ResponseHeader.KEY_CODE));
                    } else {
                        syncStatusExtra.setProgramvalue(InvCountExtraConstants.Value.ProgramValue.ERROR);
                        syncMsgExtra.setProgramvalue(responsePayloadJSON.getString(InvCountHeaderConstants.InterfaceSDK.WMSCounting.ResponseHeader.KEY_MSG));
                        throw new CommonException("Can't call External interface");
                    }
                } else {
                    syncStatusExtra.setProgramvalue(InvCountExtraConstants.Value.ProgramValue.SKIP);
                    syncMsgExtra.setProgramvalue("");
                }
            } catch (Exception e) {
                invCountExtras.get(0).setProgramvalue(InvCountHeaderConstants.InterfaceSDK.WMSCounting.ResponseHeader.VALUE_STATUS_ERROR);
                invCountExtras.get(1).setProgramvalue(e.getMessage());

                errorMsg = e.getMessage();
            }

            // populate info data
            if (StringUtils.isNotBlank(errorMsg)) {
                invCountHeaderDTO.setErrorMsg(errorMsg);
                invCountInfoDTO.getErrorList().add(invCountHeaderDTO);
            } else {
                invCountInfoDTO.getSuccessList().add(invCountHeaderDTO);
            }
        }
        invCountInfoDTO.setCompleteErrMsg(invCountInfoDTO.getErrorList().stream().map(InvCountHeaderDTO::getErrorMsg).collect(Collectors.joining(",")));

        // Construct the return value InvCountInfoDTO If errorList not empty, need to rollback(equals not execute the document), at the same time, need to submit the transaction about insert or update extraList data.
        if(invCountInfoDTO.getErrorList().stream().noneMatch(Objects::nonNull)){
            invCountHeaderRepository.batchUpdateOptional(invCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Sync.WMS);
        }

        // insert & update extras
        insertUpdateExtra(invCountExtras);
        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO  submitCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO= new InvCountInfoDTO();

        // get header db data
        List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(invCountHeaderDTOS.stream().map(headerDTO -> headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(",")));
        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());
                // 1. Check document status
                if (!oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.IN_COUNTING)
                        && !oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.PROCESSING)
                        && !oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.REJECTED)
                        && !oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.WITHDRAWN)) {
                    throw new CommonException("The operation is allowed only when the status in in counting, processing, rejected, withdrawn.");
                }

                // 2. current login user validation: Only the current login user is the supervisor can submit document.
                if (!Arrays.asList(oldInvCountHeaderDTO.getSupervisorIds().split(",")).contains(DetailsHelper.getUserDetails().getUserId().toString())) {
                    throw new CommonException("Only the current login user is the supervisor can submit document.");
                }

                // 3. Data integrity check
                if (oldInvCountHeaderDTO.getInvCountLineDTOList().stream().anyMatch(lineDTO -> lineDTO.getUnitQty() == null)) {
                    throw new CommonException("There are data rows with empty count quantity. Please check the data.");
                }

                if (oldInvCountHeaderDTO.getInvCountLineDTOList().stream().anyMatch(lineDTO -> !lineDTO.getUnitDiffQty().equals(BigDecimal.ZERO)) && StringUtils.isBlank(oldInvCountHeaderDTO.getReason())) {
                    throw new CommonException("When there is a difference in counting, the reason field must be entered.");
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            // populate info data
            if (StringUtils.isNotBlank(errorMsg)) {
                invCountHeaderDTO.setErrorMsg(errorMsg);
                invCountInfoDTO.getErrorList().add(invCountHeaderDTO);
            } else {
                invCountInfoDTO.getSuccessList().add(invCountHeaderDTO);
            }
            invCountInfoDTO.setCompleteErrMsg(invCountInfoDTO.getErrorList().stream().map(InvCountHeaderDTO::getErrorMsg).collect(Collectors.joining(",")));

        }
        return  invCountInfoDTO;
    }


    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeaderDTO));
    }

    @Override
    public InvCountHeader detail(Long countHeaderId) {
        return invCountHeaderRepository.selectByPrimary(countHeaderId);
    }

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // extract insert & update data
        List<InvCountHeaderDTO> insertList = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).collect(Collectors.toList());

        // insert & update data
        List<InvCountHeaderDTO> resultList= new ArrayList<>();
        if(!insertList.isEmpty()) {
           resultList.addAll(insertData(insertList));
        }
        if(!updateList.isEmpty()) {
            resultList.addAll(updateData(updateList));
        }
        return resultList;
    }
    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS){
        Integer lineMaxNumber = invCountLineRepository.selectMaxLineNumber();
        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS) {
            invCountHeaderDTO.setCountStatus(InvCountHeaderConstants.Value.CountStatus.IN_COUNTING);
            invCountHeaderDTO.setInvCountLineDTOList(new ArrayList<>());

            // get stock db data & create lines
            List<InvStockDTO> invStockDTOS = invStockRepository.selectByHeader(invCountHeaderDTO);
            for(InvStockDTO invStockDTO:invStockDTOS) {
                lineMaxNumber +=1;
                InvCountLineDTO invCountLineDTO = newLine(invCountHeaderDTO, invStockDTO, lineMaxNumber);
                invCountHeaderDTO.getInvCountLineDTOList().add(invCountLineDTO);
            }
        }
        // update headers
        invCountHeaderRepository.batchUpdateOptional(invCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Execute.EXECUTE);

        // insert lines
        invCountLineRepository.batchInsertSelective(invCountHeaderDTOS.stream().filter(headerDTO->headerDTO.getInvCountLineDTOList() !=null).flatMap(header -> header.getInvCountLineDTOList().stream()).collect(Collectors.toList()));

        return invCountHeaderDTOS;
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO){
        // Query the database based on counting order header data
        InvCountHeaderDTO oldInvCountHeaderDTO = invCountHeaderRepository.selectByPrimary(invCountHeaderDTO.getCountHeaderId());
        // Determine whether the warehouse is a WMS warehouse
        if(!oldInvCountHeaderDTO.getIsWMSWarehouse()){
            throw new CommonException("The current warehouse is not a WMS warehouse, operations are not allowed");
        }

        // Check data consistency
        Map<String,InvCountLineDTO> oldInvCountLineDTOMap = convertInvCountLineDTOSToMap(oldInvCountHeaderDTO.getInvCountLineDTOList());
        for(InvCountLineDTO invCountLineDTO:invCountHeaderDTO.getInvCountLineDTOList()){
            InvCountLineDTO oldInvCountLineDTO = oldInvCountLineDTOMap.getOrDefault(invCountLineDTO.getCountLineId().toString(),null);
            if(oldInvCountLineDTO == null){
                throw new CommonException("The counting order line data is inconsistent with the INV system, please check the data");
            }
            Utils.populateNullFields(oldInvCountLineDTO,invCountLineDTO);
            invCountLineDTO.setObjectVersionNumber(oldInvCountLineDTO.getObjectVersionNumber());
            invCountLineDTO.setUnitDiffQty(invCountLineDTO.getUnitQty().subtract(oldInvCountLineDTO.getSnapshotUnitQty()));
        }

        // Update the line data
        invCountLineRepository.batchUpdateOptional(invCountHeaderDTO.getInvCountLineDTOList(),InvCountLineConstants.UpdateOptional.Sync.RESULT);
        invCountHeaderDTO.setStatus(InvCountHeaderConstants.InterfaceSDK.WMSCounting.SUCCESS_STATUS);
        return invCountHeaderDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> executeAndCountSyncWMS(List<InvCountHeaderDTO> invCountHeaderDTOS){
        // save check
        InvCountInfoDTO invCountInfoDTO = manualSaveCheck(invCountHeaderDTOS);
        if(StringUtils.isNotBlank(invCountInfoDTO.getCompleteErrMsg())){
            throw new CommonException("Validation error: "+invCountInfoDTO.getCompleteErrMsg());
        }

        // save
        List<InvCountHeaderDTO> savedInvCountHeaderDTOS =  manualSave(invCountHeaderDTOS);

        // execute check
        InvCountInfoDTO execInvCountInfoDTO = executeCheck(savedInvCountHeaderDTOS);
        if(StringUtils.isNotBlank(execInvCountInfoDTO.getCompleteErrMsg())){
            throw new CommonException("Validation error: "+execInvCountInfoDTO.getCompleteErrMsg());
        }

        // execute
        List<InvCountHeaderDTO> executedInvCountHeaderDTOS = execute(savedInvCountHeaderDTOS);

        // sync wms
        InvCountInfoDTO syncInvCountInfoDTO = countSyncWMS(executedInvCountHeaderDTOS);
        if(StringUtils.isNotBlank(syncInvCountInfoDTO.getCompleteErrMsg())){
            throw new CommonException("Validation error: "+syncInvCountInfoDTO.getErrorList());
        }

        return executedInvCountHeaderDTOS;
    }

    @Override
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderDTOS){
        InvCountHeaderDTO listParam = new InvCountHeaderDTO();
        listParam.setIds(invCountHeaderDTOS.stream().map(headerDTO->headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(",")));
        List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectList(listParam);
        Map<String,InvCountHeaderDTO> oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        // Get configuration file value
        String workflowFlag = profileClient.getProfileValueByOptions(DetailsHelper.getUserDetails().getTenantId(), null, null,InvCountHeaderConstants.Profile.CountingWorkflow.NAME);
        // Determine whether to start workflow
        if(workflowFlag.equals(InvCountHeaderConstants.Profile.CountingWorkflow.ENABLED)) {
            // start workflow
            for(InvCountHeaderDTO invCountHeaderDTO:invCountHeaderDTOS){
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());
                Utils.populateNullFields(oldInvCountHeaderDTO,invCountHeaderDTO);
                Map<String,Object> workflowVariableMap = new HashMap<>();
                workflowVariableMap.put(InvCountHeaderConstants.Workflow.Submit.VAR_DEPARTMENT_CODE,oldInvCountHeaderDTO.getDepartmentCode());

                workflowClient.startInstanceByFlowKey(invCountHeaderDTO.getTenantId(), InvCountHeaderConstants.Workflow.Submit.FLOW_KEY, invCountHeaderDTO.getCountNumber(), InvCountHeaderConstants.Workflow.Submit.DIMENSION, InvCountHeaderConstants.Workflow.Submit.STARTER, workflowVariableMap);
            }
        } else {
            // update document status to confirmed
            for(InvCountHeaderDTO invCountHeaderDTO:invCountHeaderDTOS){
                invCountHeaderDTO.setCountStatus(InvCountHeaderConstants.Value.CountStatus.CONFIRMED);
            }
            invCountHeaderRepository.batchUpdateOptional(invCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Submit.SUBMIT);
        }
        return invCountHeaderDTOS;
    }

    @Override
    public InvCountHeaderDTO submitApproval(WorkFlowEventDTO workflowEventDTO) {
        InvCountHeaderDTO listParam = new InvCountHeaderDTO();
        listParam.setCountNumber(workflowEventDTO.getBusinessKey());
        InvCountHeaderDTO invCountHeaderDTO = invCountHeaderRepository.selectList(listParam).get(0);
        invCountHeaderDTO.setCountStatus(workflowEventDTO.getDocStatus());
        invCountHeaderDTO.setWorkflowId(workflowEventDTO.getWorkflowId());
        if(workflowEventDTO.getDocStatus().equals(InvCountHeaderConstants.Value.CountStatus.APPROVED)){
            invCountHeaderDTO.setApprovedTime(workflowEventDTO.getApprovedTime());
        }
        invCountHeaderRepository.updateOptional(invCountHeaderDTO, InvCountHeaderConstants.UpdateOptional.Submit.APPROVE);
        return  invCountHeaderDTO;
    }

    @Override
    public List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO invCountHeaderDTO){
        List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectList(invCountHeaderDTO);
        for(InvCountHeaderDTO oldInvCountHeaderDTO: oldInvCountHeaderDTOS){
            List<RunTaskHistory> runTaskHistory = workflowClient.approveHistory(oldInvCountHeaderDTO.getTenantId(),oldInvCountHeaderDTO.getWorkflowId());
            oldInvCountHeaderDTO.setApprovalHistoryList(runTaskHistory);
            oldInvCountHeaderDTO.setCounterNamesString(oldInvCountHeaderDTO.getCounterList().stream().map(User::getRealName).collect(Collectors.joining(", ")));
            oldInvCountHeaderDTO.setSupervisorNamesString(oldInvCountHeaderDTO.getSupervisorList().stream().map(User::getRealName).collect(Collectors.joining(", ")));
            oldInvCountHeaderDTO.setMaterialCodesString(oldInvCountHeaderDTO.getSnapshotMaterialList().stream().map(InvMaterial::getMaterialCode).collect(Collectors.joining(", ")));
            oldInvCountHeaderDTO.setBatchCodesString(oldInvCountHeaderDTO.getSnapshotBatchList().stream().map(InvBatch::getBatchCode).collect(Collectors.joining(", ")));
            for(InvCountLineDTO oldInvCountLineDTO:oldInvCountHeaderDTO.getInvCountLineDTOList()){
                oldInvCountLineDTO.setCounterNamesString(oldInvCountLineDTO.getCounterList().stream().map(User::getRealName).collect(Collectors.joining(", ")));
            }
        }
        return oldInvCountHeaderDTOS;
    }

    private List<InvCountHeaderDTO> insertData(List<InvCountHeaderDTO> invCountHeaderDTOS){
        // Insert Headers
        Map<String,String> coderuleVariableMap = new HashMap<>();
        coderuleVariableMap.put(InvCountHeaderConstants.CodeRule.CountNumber.CUSTOM_SEGMENT_KEY,DetailsHelper.getUserDetails().getTenantId().toString());
        List<String> headerNumbers = codeRuleBuilder.generateCode(invCountHeaderDTOS.size(), InvCountHeaderConstants.CodeRule.CountNumber.CODE,coderuleVariableMap);
        for(int i=0;i< invCountHeaderDTOS.size();i++){
            InvCountHeaderDTO invCountHeaderDTO = invCountHeaderDTOS.get(i);
            invCountHeaderDTO.setTenantId(DetailsHelper.getUserDetails().getTenantId());
            invCountHeaderDTO.setCountNumber(headerNumbers.get(i));
            invCountHeaderDTO.setDelFlag(InvCountHeaderConstants.Value.DelFlag.DEFAULT);
            invCountHeaderDTO.setCountStatus(InvCountHeaderConstants.Value.CountStatus.DEFAULT);
        }
        return invCountHeaderRepository.batchInsertSelective(invCountHeaderDTOS);
    }

    private List<InvCountHeaderDTO> updateData(List<InvCountHeaderDTO> invCountHeaderDTOS){
        // Populate header input null values with old ones
        List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(invCountHeaderDTOS.stream().map(headerDTO->headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(",")));
        Map<String,InvCountHeaderDTO> oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        for (InvCountHeaderDTO invCountHeaderDTO:invCountHeaderDTOS){
            InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());
            Utils.populateNullFields(oldInvCountHeaderDTO, invCountHeaderDTO);
        }

        // Update headers
        List<InvCountHeaderDTO> draftInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.DRAFT)).collect(Collectors.toList());
        List<InvCountHeaderDTO> inCountingInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.IN_COUNTING)).collect(Collectors.toList());
        List<InvCountHeaderDTO> rejectedInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.REJECTED)).collect(Collectors.toList());
        List<InvCountHeaderDTO> withdrawInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.Value.CountStatus.WITHDRAWN)).collect(Collectors.toList());

        List<InvCountHeaderDTO> updatedDraftInvCountHeaderDTOS = invCountHeaderRepository.batchUpdateOptional(draftInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Save.DRAFT);
        List<InvCountHeaderDTO> updatedInCountingInvCountHeaderDTOS =invCountHeaderRepository.batchUpdateOptional(inCountingInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Save.IN_COUNTING);
        List<InvCountHeaderDTO> updatedRejectedInvCountHeaderDTOS =invCountHeaderRepository.batchUpdateOptional(rejectedInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Save.REJECTED);
        List<InvCountHeaderDTO> updatedWithdrawInvCountHeaderDTOS =invCountHeaderRepository.batchUpdateOptional(withdrawInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.Save.WITHDRAW);

        // update lines
        List<InvCountLineDTO> updateLineDTOS =  invCountHeaderDTOS.stream().filter(headerDTO->headerDTO.getInvCountLineDTOList() !=null).flatMap(header -> header.getInvCountLineDTOList().stream()).filter(lineDTO ->lineDTO.getCountLineId() != null).collect(Collectors.toList());
        String lineIds = updateLineDTOS.stream().map(lineDTO -> lineDTO.getCountLineId().toString()).collect(Collectors.joining(","));
        Map<String,InvCountLineDTO> oldInvCountLineDTOMap = new HashMap<>();
        if(!lineIds.isEmpty()){
            List<InvCountLineDTO> oldInvCountLineDTOS = invCountLineRepository.selectByIds(lineIds);
            oldInvCountLineDTOMap = convertInvCountLineDTOSToMap(oldInvCountLineDTOS);
        }
        for (InvCountLineDTO invCountLineDTO:updateLineDTOS){
            InvCountLineDTO oldInvCountLineDTO =oldInvCountLineDTOMap.get(invCountLineDTO.getCountLineId().toString());
            Utils.populateNullFields(oldInvCountLineDTO, invCountLineDTO);
            invCountLineDTO.setCounterIds(DetailsHelper.getUserDetails().getUserId().toString());
        }
        invCountLineRepository.batchUpdateOptional(updateLineDTOS,InvCountLineConstants.UpdateOptional.Save.IN_COUNTING);

        // return all headers
        List<InvCountHeaderDTO> combinedInvCountHeaderDTOS = new ArrayList<>();
        combinedInvCountHeaderDTOS.addAll(updatedDraftInvCountHeaderDTOS);
        combinedInvCountHeaderDTOS.addAll(updatedInCountingInvCountHeaderDTOS);
        combinedInvCountHeaderDTOS.addAll(updatedRejectedInvCountHeaderDTOS);
        combinedInvCountHeaderDTOS.addAll(updatedWithdrawInvCountHeaderDTOS);
        return combinedInvCountHeaderDTOS;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void insertUpdateExtra(List<InvCountExtra> invCountExtras){
        // insert & update extras
        invCountExtraRepository.batchInsertSelective(invCountExtras.stream().filter(extra-> extra.getExtrainfoid()==null).collect(Collectors.toList()));
        invCountExtraRepository.batchUpdateByPrimaryKeySelective(invCountExtras.stream().filter(extra-> extra.getExtrainfoid()!=null).collect(Collectors.toList()));
    }

    private Map<String,InvCountHeaderDTO> convertInvCountHeaderDTOSToMap(List<InvCountHeaderDTO> invCountHeaderDTOS){
        Map<String,InvCountHeaderDTO> invCountHeaderDTOMap = new HashMap<>();
        for(InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS){
            invCountHeaderDTOMap.put(invCountHeaderDTO.getCountHeaderId().toString(),invCountHeaderDTO);
        }
        return invCountHeaderDTOMap;
    }

    private  Map<String,InvCountLineDTO> convertInvCountLineDTOSToMap(List<InvCountLineDTO> invCountLineDTOS){
        Map<String,InvCountLineDTO> invCountLineDTOMap = new HashMap<>();
        for(InvCountLineDTO invCountLineDTO: invCountLineDTOS){
            invCountLineDTOMap.put(invCountLineDTO.getCountLineId().toString(),invCountLineDTO);
        }
        return invCountLineDTOMap;
    }

    private Map<String, InvWarehouse> convertInvWarehouseToMap(List<InvWarehouse> invWarehouses){
        Map<String,InvWarehouse> invWarehouseMap = new HashMap<>();
        for(InvWarehouse invWarehouse: invWarehouses){
            invWarehouseMap.put(invWarehouse.getWarehouseId().toString(),invWarehouse);
        }
        return invWarehouseMap;
    }

    private InvCountLineDTO newLine(InvCountHeaderDTO invCountHeaderDTO, InvStockDTO invStockDTO, Integer lineNumber) {
        InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
        invCountLineDTO.setTenantId(invCountHeaderDTO.getTenantId());
        invCountLineDTO.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
        invCountLineDTO.setLineNumber(lineNumber);
        invCountLineDTO.setWarehouseId(invCountHeaderDTO.getWarehouseId());
        invCountLineDTO.setMaterialId(invStockDTO.getMaterialId());
        invCountLineDTO.setUnitCode(invStockDTO.getUnitCode());
        invCountLineDTO.setBatchId(invStockDTO.getBatchId());
        invCountLineDTO.setSnapshotUnitQty(invStockDTO.getUnitQuantity());
        invCountLineDTO.setCounterIds(invCountHeaderDTO.getCounterIds());
        return invCountLineDTO;
    }

    private InvCountExtra newExtra(InvCountHeaderDTO invCountHeaderDTO, String programKey){
        InvCountExtra invCountExtra = new InvCountExtra();
        invCountExtra.setTenantid(invCountHeaderDTO.getTenantId());
        invCountExtra.setSourceid(invCountHeaderDTO.getCountHeaderId());
        invCountExtra.setEnabledflag(InvCountExtraConstants.Value.EnabledFlag.DEFAULT);
        invCountExtra.setProgramkey(programKey);
        return invCountExtra;
    }
}

