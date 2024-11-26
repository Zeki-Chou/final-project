package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvMaterial;

import java.util.List;

/**
 * (InvMaterial)应用服务
 *
 * @author
 * @since 2024-11-25 13:43:52
 */
public interface InvMaterialMapper extends BaseMapper<InvMaterial> {
    /**
     * 基础查询
     *
     * @param invMaterial 查询条件
     * @return 返回值
     */
    List<InvMaterial> selectList(InvMaterial invMaterial);
}
