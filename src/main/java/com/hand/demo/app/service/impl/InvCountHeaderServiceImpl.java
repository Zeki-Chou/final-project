package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.*;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.entity.InvMaterial;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.AllArgsConstructor;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.hzero.mybatis.domian.Condition;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;

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

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public void saveData(List<InvCountHeader> invCountHeaders) {
        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
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

        Map<Long, InvWarehouse> invWarehouseById = getWarehouseMappedById(headerDTOList);

        for(InvCountHeaderDTO headerDTO : headerDTOList) {
            if (headerDTO.getCountHeaderId() == null) {
                return null;
            }

            String countStatus = headerDTO.getCountStatus();

            if(!countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT) && allowedCountStatusList.contains(countStatus)) {
                setErrorCountInfoError(headerDTO, infoDTO, "only draft, in counting, rejected, and withdrawn status can be modified");
            } else if(countStatus.equals(InvCountHeaderConstants.COUNT_STATUS_DRAFT) && !isCreator(headerDTO.getCreatedBy())) {
                setErrorCountInfoError(headerDTO, infoDTO, "Document in draft status can only be modified by the document creator.");
            } else if (allowedCountStatusList.contains(countStatus)) {
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

        invCountHeaderRepository.batchDelete(new ArrayList<>(headerDTOList));
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
}

