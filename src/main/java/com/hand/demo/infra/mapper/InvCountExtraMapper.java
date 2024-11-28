package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvCountExtra;

import java.util.List;

/**
 * (InvCountExtra)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-28 17:11:49
 */
public interface InvCountExtraMapper extends BaseMapper<InvCountExtra> {
    /**
     * 基础查询
     * @param invCountExtra 查询条件
     * @return 返回值
     */
    List<InvCountExtra> selectList(InvCountExtra invCountExtra);
}

