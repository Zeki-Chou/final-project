package com.hand.demo.domain.repository;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)资源库
 *
 * @author
 * @since 2024-11-25 09:59:38
 */
public interface InvCountHeaderRepository extends BaseRepository<InvCountHeaderDTO> {
    /**
     * 查询
     *
     * @param invCountHeaderDTO 查询条件
     * @return 返回值
     */
    List<InvCountHeaderDTO> selectList(InvCountHeaderDTO invCountHeaderDTO);

    /**
     * 根据主键查询（可关联表）
     *
     * @param countHeaderId 主键
     * @return 返回值
     */
    InvCountHeaderDTO selectByPrimary(Long countHeaderId);
}
