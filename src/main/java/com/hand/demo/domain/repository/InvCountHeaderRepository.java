package com.hand.demo.domain.repository;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)资源库
 *
 * @author
 * @since 2024-11-25 10:19:43
 */
public interface InvCountHeaderRepository extends BaseRepository<InvCountHeader> {
    /**
     * 查询
     *
     * @param invCountHeader 查询条件
     * @return 返回值
     */
    List<InvCountHeaderDTO> selectList(InvCountHeaderDTO invCountHeader);

    /**
     * 根据主键查询（可关联表）
     *
     * @param countHeaderId 主键
     * @return 返回值
     */
    InvCountHeaderDTO selectByPrimary(Long countHeaderId);
}
