package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.*;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvMaterial;
import com.hand.demo.domain.entity.InvWarehouse;
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
import com.hand.demo.domain.entity.InvCountHeader;

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

    private InvCountLineRepository invCountLineRepository;

    private InvMaterialRepository invMaterialRepository;

    private InvBatchRepository invBatchRepository;

    private final LovAdapter lovAdapter;

    private final CodeRuleBuilder codeRuleBuilder;

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
        variableMap.put("customSegment", "-");
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
                .setInvCountLineDTOList(invCountLineDTOList);
        return invCountHeaderDTO;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        List<String> allowedCountStatusList = getAllowedLovValues();


        List<InvCountHeaderDTO> headerInDatabaseDTO = getHeaderDTOsFromDb(headerDTOList);

        for(InvCountHeaderDTO headerDTO : headerInDatabaseDTO) {
            if (headerDTO.getCountHeaderId() == null) {
                continue;
            }

            String countStatus = headerDTO.getCountStatus();

            if(!countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT) && allowedCountStatusList.contains(countStatus)) {
                setErrorCountInfoError(headerDTO, infoDTO, "only draft, in counting, rejected, and withdrawn status can be modified");
            } else if(countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT) && !isCreator(headerDTO.getCreatedBy())) {
                setErrorCountInfoError(headerDTO, infoDTO, "Document in draft status can only be modified by the document creator.");
            } else if (allowedCountStatusList.contains(countStatus)) {
                Map<Long, InvWarehouse> invWarehouseById = getWarehouseMappedById(headerDTOList);

                boolean isWMSWarehouse = invWarehouseById.get(headerDTO.getWarehouseId()).getIsWmsWarehouse() == 1;
                List<Long> parsedSupervisorIds = parseCommaSeperatedIds(headerDTO.getSupervisorIds().toString());
                List<Long> parsedCounterIds = parseCommaSeperatedIds(headerDTO.getCounterIds().toString());
                if(isWMSWarehouse && !isSupervisor(parsedSupervisorIds)) {
                    setErrorCountInfoError(headerDTO, infoDTO, "The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
                } else if(!isCounter(parsedCounterIds) && !isSupervisor(parsedSupervisorIds) && !isCreator(headerDTO.getCreatedBy())) {
                    setErrorCountInfoError(headerDTO, infoDTO, "only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn.");
                }
            } else {
                infoDTO.getSuccessList().add(headerDTO);
            }
        }

        List<InvCountHeaderDTO> errors = infoDTO.getErrorList();
        if (!errors.isEmpty()) {
            throw new CommonException(errors.toString());
        }

        return infoDTO;

    }

    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> headerDTOList) {
        InvCountInfoDTO infoDTO = new InvCountInfoDTO();

        for(InvCountHeaderDTO headerDTO : headerDTOList) {
            if (!headerDTO.getCountStatus().equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT)) {
                setErrorCountInfoError(headerDTO, infoDTO, "Only allow draft status to be deleted");
            } else if (!getCurrentUser().getUserId().equals(headerDTO.getCreatedBy())) {
                setErrorCountInfoError(headerDTO, infoDTO, "Only current user is document creator allow delete document");
            }
        }

        List<InvCountHeaderDTO> errors = infoDTO.getErrorList();
        if (!errors.isEmpty()) {
            throw new CommonException(errors.toString());
        }

        invCountHeaderRepository.batchDeleteByPrimaryKey(new ArrayList<>(headerDTOList));
        return infoDTO;
    }

    private List<String> getAllowedLovValues() {
        return lovAdapter.queryLovValue(InvCountHeaderConstants.INV_COUNT_HEADER_COUNT_STATUS, BaseConstants.DEFAULT_TENANT_ID)
                .stream()
                .map(LovValueDTO::getValue)
                .filter(InvCountHeaderConstants.HEADER_COUNT_DESIRED_STATUSES::contains)
                .collect(Collectors.toList());
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

    private CustomUserDetails getCurrentUser() {
        return DetailsHelper.getUserDetails();
    }

    private List<Long> parseCommaSeperatedIds(String ids) {
        return Arrays.stream(ids.split(",")).map(Long::parseLong).collect(Collectors.toList());
    }

    private boolean isSupervisor(List<Long> supervisorIds) {
        return supervisorIds.contains(getCurrentUser().getUserId());
    }

    private boolean isCounter(List<Long> counterIds) {
        return counterIds.contains(getCurrentUser().getUserId());
    }

    private boolean isCreator(Long creator) {
        return getCurrentUser().getUserId().equals(creator);
    }

    private void setErrorCountInfoError(InvCountHeaderDTO headerDTO, InvCountInfoDTO infoDTO, String errorMessage) {
        headerDTO.setErrorMessage(errorMessage);
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

