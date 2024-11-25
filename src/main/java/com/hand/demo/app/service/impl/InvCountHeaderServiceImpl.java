package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * (InvCountHeader)应用服务
 *
 * @author Allan
 * @since 2024-11-25 08:19:18
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {

    private final InvCountHeaderRepository invCountHeaderRepository;
    private final CodeRuleBuilder codeRuleBuilder;

    public InvCountHeaderServiceImpl(InvCountHeaderRepository invCountHeaderRepository, CodeRuleBuilder codeRuleBuilder) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.codeRuleBuilder = codeRuleBuilder;
    }

    @Override
    public Page<InvCountHeader> selectList(PageRequest pageRequest, InvCountHeader invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        // insert count status default draft
        for (InvCountHeader header : insertList) {
            String countNumber = generateCountNumber();
            header.setCountStatus(Constants.InvCountHeader.DEFAULT_COUNT_STATUS);
            header.setCountNumber(countNumber);
            header.setDelFlag(BaseConstants.Flag.NO);
        }

        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        invCountHeaderRepository.batchInsertSelective(insertList);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);

        return invCountHeaders;
    }

    /**
     * generate invoice header number
     * @return invoice header number with format
     */
    private String generateCountNumber() {
        Map<String, String> variableMap = new HashMap<>();
        variableMap.put("customSegment", String.valueOf(BaseConstants.DEFAULT_TENANT_ID));
        return codeRuleBuilder.generateCode(Constants.InvCountHeader.CODE_RULE, variableMap);
    }

}

