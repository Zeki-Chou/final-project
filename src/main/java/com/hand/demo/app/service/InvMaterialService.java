package com.hand.demo.app.service;

import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvMaterial;

import java.util.List;

/**
 * (InvMaterial)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:14:37
 */
public interface InvMaterialService {

    /**
     * 查询数据
     *
     * @param pageRequest 分页参数
     * @param invMaterials 查询条件
     * @return 返回值
     */
    Page<InvMaterial> selectList(PageRequest pageRequest, InvMaterial invMaterials);

    /**
     * 保存数据
     * @param invMaterials 数据
     */
    void saveData(List<InvMaterial> invMaterials);

}

