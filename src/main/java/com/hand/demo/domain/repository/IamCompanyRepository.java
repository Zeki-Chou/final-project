package com.hand.demo.domain.repository;

import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.IamCompany;

import java.util.List;

/**
 * (IamCompany)资源库
 *
 * @author
 * @since 2024-11-25 11:46:46
 */
public interface IamCompanyRepository extends BaseRepository<IamCompany> {
    /**
     * 查询
     *
     * @param iamCompany 查询条件
     * @return 返回值
     */
    List<IamCompany> selectList(IamCompany iamCompany);

    /**
     * 根据主键查询（可关联表）
     *
     * @param companyId 主键
     * @return 返回值
     */
    IamCompany selectByPrimary(Long companyId);
}
