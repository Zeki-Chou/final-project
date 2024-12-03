package com.hand.demo.infra.mapper;

import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.User;

import java.util.List;

/**
 * (User)应用服务
 *
 * @author
 * @since 2024-12-03 14:20:06
 */
public interface UserMapper extends BaseMapper<User> {
    /**
     * 基础查询
     * @param user 查询条件
     * @return 返回值
     */
    List<User> selectList(User user);
}

