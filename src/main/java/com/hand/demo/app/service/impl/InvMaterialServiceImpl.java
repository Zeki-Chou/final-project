package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.MaterialInfoDTO;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.app.service.InvMaterialService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvMaterial;
import com.hand.demo.domain.repository.InvMaterialRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvMaterial)应用服务
 *
 * @author Allan
 * @since 2024-11-26 13:45:20
 */
@Service
public class InvMaterialServiceImpl implements InvMaterialService {

    private final InvMaterialRepository invMaterialRepository;

    public InvMaterialServiceImpl(InvMaterialRepository invMaterialRepository) {
        this.invMaterialRepository = invMaterialRepository;
    }

    @Override
    public Page<InvMaterial> selectList(PageRequest pageRequest, InvMaterial invMaterial) {
        return PageHelper.doPageAndSort(pageRequest, () -> invMaterialRepository.selectList(invMaterial));
    }

    @Override
    public void saveData(List<InvMaterial> invMaterials) {
        List<InvMaterial> insertList = invMaterials.stream().filter(line -> line.getMaterialId() == null).collect(Collectors.toList());
        List<InvMaterial> updateList = invMaterials.stream().filter(line -> line.getMaterialId() != null).collect(Collectors.toList());
        invMaterialRepository.batchInsertSelective(insertList);
        invMaterialRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public List<MaterialInfoDTO> convertMaterialIdsToList(String materialIds) {
        return invMaterialRepository.selectByIds(materialIds)
                .stream()
                .map(this::createNewMaterialInfoDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<InvMaterial> findMaterialsByListIds(List<Long> ids) {
        return invMaterialRepository.selectByIds(Utils.generateStringIds(ids));
    }

    private MaterialInfoDTO createNewMaterialInfoDTO(InvMaterial material) {
        return new MaterialInfoDTO(material.getMaterialId(), material.getMaterialCode());
    }
}

