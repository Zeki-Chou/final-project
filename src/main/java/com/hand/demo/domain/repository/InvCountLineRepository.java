package com.hand.demo.domain.repository;

import com.hand.demo.api.dto.InvCountLineDTO;
import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvCountLine;

import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author Allan
 * @since 2024-11-25 15:46:33
 */
public interface InvCountLineRepository extends BaseRepository<InvCountLine> {
    /**
     * 查询
     *
     * @param invCountLine 查询条件
     * @return 返回值
     */
    List<InvCountLineDTO> selectList(InvCountLine invCountLine);

    /**
     * 根据主键查询（可关联表）
     *
     * @param countLineId 主键
     * @return 返回值
     */
    InvCountLineDTO selectByPrimary(Long countLineId);

    /**
     * @param headerIds
     * @return
     */
    List<InvCountLineDTO> selectByCountHeaderIds(List<Long> headerIds);

    /**
     * @return
     */
    Integer selectHighestLineNumber();

    List<InvCountLineDTO> selectLineReport(List<Long> headerIds);
}
