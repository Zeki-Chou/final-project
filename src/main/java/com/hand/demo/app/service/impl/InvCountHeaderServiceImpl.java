package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.AllArgsConstructor;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.mybatis.domian.Condition;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
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

    private final LovAdapter lovAdapter;

    private final CodeRuleBuilder codeRuleBuilder;

    private final InvCountLineService invCountLineService;

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


        List<InvCountLineDTO> linesToSave = new ArrayList<>();
        invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
        invCountHeaders.forEach(header -> {
            List<InvCountLineDTO> invCountLines = header.getInvCountLineDTOList();
            if (invCountLines != null && !invCountLines.isEmpty()) {
                invCountLines.forEach(line -> {
                    line.setCountHeaderId(header.getCountHeaderId());
                    line.setCounterIds(header.getCounterIds());
                    linesToSave.add(line);
                });
            }
        });

        if (!linesToSave.isEmpty()) {
            invCountLineService.saveData(linesToSave);
        }

//        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateList));
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
                .setInvCountLineDTOList(invCountLineDTOList)
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

            valueSetValidation(infoDTO, headerDTOFromInput);

            companyDepartmentWarehouseValidation(headerDTOList, headerDTOFromInput, infoDTO);

            List<Long> materialIds = Arrays.stream(
                    headerDTO.getSnapshotMaterialList()
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

        return Collections.emptyList();
    }

    private List<CounterDTO> getCounters(String counterIds) {
        List<String> ids = Arrays.asList(counterIds.split(","));
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

    private void companyDepartmentWarehouseValidation(List<InvCountHeaderDTO> headerDTOList, InvCountHeaderDTO headerDTOFromInput, InvCountInfoDTO infoDTO) {
        String companyIds = headerDTOList.stream()
                .map(InvCountHeaderDTO::getCompanyId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String departmentIds = headerDTOList.stream()
                .map(InvCountHeaderDTO::getDepartmentId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String warehouseIds = headerDTOList.stream()
                .map(InvCountHeaderDTO::getCompanyId)
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
}

