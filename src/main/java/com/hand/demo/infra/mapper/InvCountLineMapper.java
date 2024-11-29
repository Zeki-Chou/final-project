package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvCountLine;

import java.util.List;

/**
 * (InvCountLine)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:12:57
 */
public interface InvCountLineMapper extends BaseMapper<InvCountLine> {
    /**
     * 基础查询
     * @param invCountLine 查询条件
     * @return 返回值
     */
    List<InvCountLine> selectList(InvCountLine invCountLine);

    /**
     * select Last Number
     * @return LineNumber
     */
    Integer selectLastNumber();
}

