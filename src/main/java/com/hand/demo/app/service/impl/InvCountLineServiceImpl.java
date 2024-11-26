package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author
 * @since 2024-11-26 08:19:51
 */
@Service
public class InvCountLineServiceImpl implements InvCountLineService {
    @Autowired
    private InvCountLineRepository invCountLineRepository;

    @Override
    public Page<InvCountLineDTO> selectList(PageRequest pageRequest, InvCountLineDTO invCountLineDTO) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLineDTO));
    }

    @Override
    public void saveData(List<InvCountLineDTO> invCountLineDTOS) {
        List<InvCountLineDTO> insertList = invCountLineDTOS.stream().filter(lineDTO -> lineDTO.getCountLineId() == null).collect(Collectors.toList());
        List<InvCountLineDTO> updateList = invCountLineDTOS.stream().filter(lineDTO -> lineDTO.getCountLineId() != null).collect(Collectors.toList());
        invCountLineRepository.batchInsertSelective(insertList);
        invCountLineRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

}

