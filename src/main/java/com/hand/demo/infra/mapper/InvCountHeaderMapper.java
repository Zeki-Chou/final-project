package com.hand.demo.infra.mapper;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 09:59:38
 */
public interface InvCountHeaderMapper extends BaseMapper<InvCountHeaderDTO> {
    /**
     * 基础查询
     *
     * @param invCountHeaderDTO 查询条件
     * @return 返回值
     */
    List<InvCountHeaderDTO> selectList(InvCountHeaderDTO invCountHeaderDTO);
}

