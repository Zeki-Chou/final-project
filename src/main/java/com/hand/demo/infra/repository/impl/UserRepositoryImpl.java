package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.User;
import com.hand.demo.domain.repository.UserRepository;
import com.hand.demo.infra.mapper.UserMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (User)资源库
 *
 * @author
 * @since 2024-12-03 14:20:07
 */
@Component
public class UserRepositoryImpl extends BaseRepositoryImpl<User> implements UserRepository {
    @Resource
    private UserMapper userMapper;

    @Override
    public List<User> selectList(User user) {
        return userMapper.selectList(user);
    }

    @Override
    public User selectByPrimary(Long id) {
        User user = new User();
        user.setId(id);
        List<User> users = userMapper.selectList(user);
        if (users.size() == 0) {
            return null;
        }
        return users.get(0);
    }

}

