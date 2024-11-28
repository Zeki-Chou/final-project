package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.IamCompany;

import java.util.List;

/**
 * (IamCompany)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:13:31
 */
public interface IamCompanyMapper extends BaseMapper<IamCompany> {
    /**
     * 基础查询
     * @param iamCompany 查询条件
     * @return 返回值
     */
    List<IamCompany> selectList(IamCompany iamCompany);
}

