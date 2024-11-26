package com.hand.demo.domain.repository;

import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvCountLine;

import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author muhammad.azzam@hand-global.com
 * @since 2024-11-25 10:20:03
 */
public interface InvCountLineRepository extends BaseRepository<InvCountLine> {
    /**
     * 查询
     *
     * @param invCountLine 查询条件
     * @return 返回值
     */
    List<InvCountLine> selectList(InvCountLine invCountLine);

    Long maxLineNumber();

    /**
     * 根据主键查询（可关联表）
     *
     * @param countLineId 主键
     * @return 返回值
     */
    InvCountLine selectByPrimary(Long countLineId);
}
