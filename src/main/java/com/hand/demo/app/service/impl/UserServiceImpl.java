package com.hand.demo.app.service.impl;

import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.UserService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.User;
import com.hand.demo.domain.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (User)应用服务
 *
 * @author
 * @since 2024-12-03 14:20:07
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

    @Override
    public Page<User> selectList(PageRequest pageRequest, User user) {
        return PageHelper.doPageAndSort(pageRequest, () -> userRepository.selectList(user));
    }

    @Override
    public void saveData(List<User> users) {
        List<User> insertList = users.stream().filter(line -> line.getId() == null).collect(Collectors.toList());
        List<User> updateList = users.stream().filter(line -> line.getId() != null).collect(Collectors.toList());
        userRepository.batchInsertSelective(insertList);
        userRepository.batchUpdateByPrimaryKeySelective(updateList);
    }
}

