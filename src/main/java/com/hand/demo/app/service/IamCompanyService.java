package com.hand.demo.app.service;

import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.IamCompany;

import java.util.List;

/**
 * (IamCompany)应用服务
 *
 * @author azhar.naufal@hand-global.com
 * @since 2024-11-25 11:13:31
 */
public interface IamCompanyService {

    /**
     * 查询数据
     *
     * @param pageRequest 分页参数
     * @param iamCompanys 查询条件
     * @return 返回值
     */
    Page<IamCompany> selectList(PageRequest pageRequest, IamCompany iamCompanys);

    /**
     * 保存数据
     * @param iamCompanys 数据
     */
    void saveData(List<IamCompany> iamCompanys);

}

