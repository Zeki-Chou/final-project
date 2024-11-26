package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.api.dto.InvStockDTO;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import com.hand.demo.infra.constant.InvCountLineConstants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaderDTO) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeaderDTO));
    }

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<InvCountHeaderDTO> insertList = invCountHeaderDTOS.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaderDTOS.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        insertData(insertList);
        updateData(updateList);
        return invCountHeaderDTOS;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO(invCountHeaderDTOS.size());
        invCountInfoDTO.setErrorList(Arrays.asList(new String[invCountHeaderDTOS.size()]));

        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = new HashMap<>();
        String oldHeaderIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).map(headerDTO -> headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(","));
        if (!oldHeaderIds.isEmpty()) {
            List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(oldHeaderIds);
            oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        }
        for (int i = 0; i < invCountHeaderDTOS.size(); i++) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO invCountHeaderDTO = invCountHeaderDTOS.get(i);
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());

                // skip insert headers
                if (invCountHeaderDTO.getCountHeaderId() == null) {
                    continue;
                }

                // not allow update status field
                if (invCountHeaderDTO.getCountStatus() != null) {
                    throw new CommonException("Not allow update status field");
                }

                Long currentUserId = DetailsHelper.getUserDetails().getUserId();
                if (oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_DRAFT)) {
                    // Document in draft status can only be modified by the document creator.
                    if (!oldInvCountHeaderDTO.getCreatedBy().equals(currentUserId)) {
                        throw new CommonException("Document in draft status can only be modified by the document creator.");
                    }
                } else if (oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_IN_COUNTING)
                        || oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_REJECTED)
                        || oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_WITHDRAWN)) {
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
            invCountInfoDTO.getErrorList().set(i, errorMsg);
        }
        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO(invCountHeaderDTOS.size());
        String oldHeaderIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).map(headerDTO -> headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(","));
        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = new HashMap<>();
        if (!oldHeaderIds.isEmpty()) {
            List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(oldHeaderIds);
            oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        }
        for (int i = 0; i < invCountHeaderDTOS.size(); i++) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO invCountHeaderDTO = invCountHeaderDTOS.get(i);
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());

                // status verification, Only allow draft status to be deleted
                if(!oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_DRAFT)){
                    throw new CommonException("Only allow draft status to be deleted");
                }
                // current user verification, Only current user is document creator allow delete document
                if (!oldInvCountHeaderDTO.getCreatedBy().equals(DetailsHelper.getUserDetails().getUserId())) {
                    throw new CommonException("Only current user is document creator allow delete document");
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }
            invCountInfoDTO.getErrorList().set(i, errorMsg);
        }
        return invCountInfoDTO;
    }

    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOS){
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO(invCountHeaderDTOS.size());
        String oldHeaderIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).map(headerDTO -> headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(","));
        Map<String, InvCountHeaderDTO> oldInvCountHeaderDTOMap = new HashMap<>();
        if (!oldHeaderIds.isEmpty()) {
            List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(oldHeaderIds);
            oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        }
        String oldCompanyIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCompanyId() != null).map(headerDTO -> headerDTO.getCompanyId().toString()).collect(Collectors.joining(","));
        String oldDepartmentIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getDepartmentId() != null).map(headerDTO -> headerDTO.getDepartmentId().toString()).collect(Collectors.joining(","));
        String oldWarehouseIds = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getWarehouseId() != null).map(headerDTO -> headerDTO.getWarehouseId().toString()).collect(Collectors.joining(","));
        Set<String> oldCompanySet = new HashSet<>();
        Set<String> oldDepartmentSet = new HashSet<>();
        Set<String> oldWarehouseSet = new HashSet<>();
        if(oldCompanyIds.isEmpty()){
            oldCompanySet= iamCompanyRepository.selectByIds(oldCompanyIds).stream().map(company->company.getCompanyId().toString()).collect(Collectors.toSet());
        }
        if(oldDepartmentIds.isEmpty()){
            oldDepartmentSet= iamDepartmentRepository.selectByIds(oldDepartmentIds).stream().map(department->department.getDepartmentId().toString()).collect(Collectors.toSet());
        }
        if(oldWarehouseIds.isEmpty()){
            oldWarehouseSet= warehouseRepository.selectByIds(oldWarehouseIds).stream().map(warehouse->warehouse.getWarehouseId().toString()).collect(Collectors.toSet());
        }

        for (int i = 0; i < invCountHeaderDTOS.size(); i++) {
            String errorMsg = null;
            try {
                InvCountHeaderDTO invCountHeaderDTO = invCountHeaderDTOS.get(i);
                InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());

                // a. document status validation: Only draft status can execute
                if(!oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_DRAFT)){
                    throw  new CommonException("Only draft status can execute");
                }

                // b. current login user validation: Only the document creator can execute
                if(!oldInvCountHeaderDTO.getCreatedBy().equals(DetailsHelper.getUserDetails().getUserId())){
                    throw  new CommonException("Only the document creator can execute");
                }

                // c. value set validation
                List<String> statusLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.LovCode.STATUS,DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());
                List<String> dimensionLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.LovCode.DIMENSION,DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());
                List<String> typeLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.LovCode.TYPE,DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());
                List<String> modeLovValueDTOS = lovAdapter.queryLovValue(InvCountHeaderConstants.LovCode.MODE,DetailsHelper.getUserDetails().getTenantId()).stream().map(LovValueDTO::getValue).collect(Collectors.toList());

                if(!statusLovValueDTOS.contains(invCountHeaderDTO.getCountStatus())){
                    throw  new CommonException("Invalid status value");
                }
                if (!dimensionLovValueDTOS.contains(invCountHeaderDTO.getCountDimension())) {
                    throw  new CommonException("Invalid dimension value");
                }
                if (!typeLovValueDTOS.contains(invCountHeaderDTO.getCountType())) {
                    throw  new CommonException("Invalid type value");
                }
                if (!modeLovValueDTOS.contains(invCountHeaderDTO.getCountMode())) {
                    throw  new CommonException("Invalid mode value");
                }

                // d. company, department, warehouse validation
                if(!oldCompanySet.contains(invCountHeaderDTO.getCompanyId().toString())){
                    throw  new CommonException("Company id not exist");
                }
                if(!oldDepartmentSet.contains(invCountHeaderDTO.getDepartmentId().toString())){
                    throw  new CommonException("Department id not exist");
                }
                if(!oldWarehouseSet.contains(invCountHeaderDTO.getWarehouseId().toString())){
                    throw  new CommonException("Warehouse id not exist");
                }

                // e. on hand quantity validation Query on hand quantity based on the tenant+company+department+warehouse+material+on hand quantity not equals 0, if no data is queried, error message: Unable to query on hand quantity data.
                InvStockDTO invStock = invStockRepository.selectByHeader(invCountHeaderDTO);
                if(invStock == null){
                    throw  new CommonException("Unable to query on hand quantity data.");
                }
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }
            invCountInfoDTO.getErrorList().set(i, errorMsg);
        }
        return invCountInfoDTO;
    }

    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderDTOS){
        // Update headers
        List<InvCountLineDTO> invCountLineDTOS = new ArrayList<>();
        Integer lineMaxNumber = invCountLineRepository.selectMaxLineNumber();
        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS){
            invCountHeaderDTO.setCountStatus(InvCountHeaderConstants.LovValue.STATUS_IN_COUNTING);


            lineMaxNumber +=1;
            InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
            invCountLineDTO.setTenantId(DetailsHelper.getUserDetails().getTenantId());
            invCountLineDTO.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
            invCountLineDTO.setLineNumber(lineMaxNumber);
            invCountLineDTO.setWarehouseId(invCountLineDTO.getWarehouseId());
            invCountLineDTO.setma
        }

        // Insert headers

    }
    private void insertData(List<InvCountHeaderDTO> invCountHeaderDTOS){
        // Insert Headers
        String CUSTOM_SEGMENT_KEY = "customSegment";
        Map<String,String> coderuleVariableMap = new HashMap<>();
        coderuleVariableMap.put(CUSTOM_SEGMENT_KEY,DetailsHelper.getUserDetails().getTenantId().toString());
        List<String> headerNumbers = codeRuleBuilder.generateCode(invCountHeaderDTOS.size(), InvCountHeaderConstants.CodeRule.COUNT_NUMBER,coderuleVariableMap);
        for(int i=0;i< invCountHeaderDTOS.size();i++){
            InvCountHeaderDTO invCountHeaderDTO = invCountHeaderDTOS.get(i);
            invCountHeaderDTO.setTenantId(DetailsHelper.getUserDetails().getTenantId());
            invCountHeaderDTO.setCountNumber(headerNumbers.get(i));
            invCountHeaderDTO.setDelFlag(InvCountHeaderConstants.DefaultValue.DEL_FLAG);
            invCountHeaderDTO.setCountStatus(InvCountHeaderConstants.DefaultValue.STATUS);
        }

        // Insert Lines
        List<InvCountHeaderDTO> insertedInvCountHeaderDTOS = invCountHeaderRepository.batchInsertSelective(invCountHeaderDTOS);
        Map<String,InvCountHeaderDTO> insertedInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(insertedInvCountHeaderDTOS);

        List<InvCountLineDTO> invCountLineDTOS = new ArrayList<>();
        Integer maxLineNumber = invCountLineRepository.selectMaxLineNumber();
        for (InvCountHeaderDTO invCountHeaderDTO: invCountHeaderDTOS){
            for(InvCountLineDTO invCountLineDTO:invCountHeaderDTO.getInvCountLineDTOList()){
                maxLineNumber+=1;

                InvCountHeaderDTO insertedInvCountHeaderDTO = insertedInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());
                invCountLineDTO.setLineNumber(maxLineNumber);
                invCountLineDTO.setCountHeaderId(insertedInvCountHeaderDTO.getCountHeaderId());
                invCountLineDTO.setTenantId(insertedInvCountHeaderDTO.getTenantId());
                invCountLineDTO.setCounterIds(insertedInvCountHeaderDTO.getCounterIds());

                invCountLineDTOS.add(invCountLineDTO);
            }
        }
        invCountLineRepository.batchInsertSelective(invCountLineDTOS);
    }

    private void updateData(List<InvCountHeaderDTO> invCountHeaderDTOS){
        // Populate headers null values with old ones
        List<InvCountHeaderDTO> oldInvCountHeaderDTOS = invCountHeaderRepository.selectByIds(invCountHeaderDTOS.stream().map(headerDTO->headerDTO.getCountHeaderId().toString()).collect(Collectors.joining(",")));
        Map<String,InvCountHeaderDTO> oldInvCountHeaderDTOMap = convertInvCountHeaderDTOSToMap(oldInvCountHeaderDTOS);
        for (InvCountHeaderDTO invCountHeaderDTO:invCountHeaderDTOS){
            Utils.populateNullFields(oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString()), invCountHeaderDTO);
        }

        // Update headers
        List<InvCountHeaderDTO> draftInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_DRAFT)).collect(Collectors.toList());
        List<InvCountHeaderDTO> inCountingInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_IN_COUNTING)).collect(Collectors.toList());
        List<InvCountHeaderDTO> rejectedInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_REJECTED)).collect(Collectors.toList());
        List<InvCountHeaderDTO> withdrawInvCountHeaderDTOS = invCountHeaderDTOS.stream().filter(headerDTO -> headerDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_WITHDRAWN)).collect(Collectors.toList());

        invCountHeaderRepository.batchUpdateOptional(draftInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.DRAFT);
        invCountHeaderRepository.batchUpdateOptional(inCountingInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.IN_COUNTING);
        invCountHeaderRepository.batchUpdateOptional(rejectedInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.REJECTED);
        invCountHeaderRepository.batchUpdateOptional(withdrawInvCountHeaderDTOS,InvCountHeaderConstants.UpdateOptional.WITHDRAW);

        // check for lines
        String lineIds = invCountHeaderDTOS.stream().flatMap(header -> header.getInvCountLineDTOList().stream()).map(line -> line.getCountLineId().toString()).collect(Collectors.joining(","));
        if(lineIds.isEmpty()){
            return;
        }

        // Populate lines values with old values
        List<InvCountLineDTO> draftInvCountLineDTOS = new ArrayList<>();
        List<InvCountLineDTO> inCountingInvCountLineDTOS = new ArrayList<>();
        List<InvCountLineDTO> oldInvCountLineDTOS = invCountLineRepository.selectByIds(lineIds);
        Map<String,InvCountLineDTO> oldInvCountLineDTOMap = convertInvCountLineDTOSToMap(oldInvCountLineDTOS);
        for (InvCountHeaderDTO invCountHeaderDTO:invCountHeaderDTOS){
            InvCountHeaderDTO oldInvCountHeaderDTO = oldInvCountHeaderDTOMap.get(invCountHeaderDTO.getCountHeaderId().toString());
            for (InvCountLineDTO invCountLineDTO:invCountHeaderDTO.getInvCountLineDTOList()){
                Utils.populateNullFields(oldInvCountLineDTOMap.get(invCountLineDTO.getCountLineId().toString()), invCountLineDTO);
                if(oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_DRAFT)){
                    draftInvCountLineDTOS.add(invCountLineDTO);
                } else if (oldInvCountHeaderDTO.getCountStatus().equals(InvCountHeaderConstants.LovValue.STATUS_IN_COUNTING)) {
                    inCountingInvCountLineDTOS.add(invCountLineDTO);
                }
            }
        }

        // Update lines
        invCountLineRepository.batchUpdateOptional(draftInvCountLineDTOS, InvCountLineConstants.UpdateOptional.DRAFT);
        invCountLineRepository.batchUpdateOptional(inCountingInvCountLineDTOS,InvCountLineConstants.UpdateOptional.IN_COUNTING);
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
}

