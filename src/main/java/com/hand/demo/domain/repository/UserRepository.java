package com.hand.demo.domain.repository;

import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.User;

import java.util.List;

/**
 * (User)资源库
 *
 * @author
 * @since 2024-12-03 14:20:07
 */
public interface UserRepository extends BaseRepository<User> {
    /**
     * 查询
     * @param user 查询条件
     * @return 返回值
     */
    List<User> selectList(User user);

    /**
     * 根据主键查询（可关联表）
     * @param id 主键
     * @return 返回值
     */
    User selectByPrimary(Long id);
}
