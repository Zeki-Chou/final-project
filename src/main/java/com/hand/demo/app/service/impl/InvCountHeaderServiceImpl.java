package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.app.service.InvCountLineService;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
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
    private final InvCountLineService invCountLineService;
    private final LovAdapter lovAdapter;

    public InvCountHeaderServiceImpl(InvCountHeaderRepository invCountHeaderRepository, CodeRuleBuilder codeRuleBuilder, InvCountLineService invCountLineService, LovAdapter lovAdapter) {
        this.invCountHeaderRepository = invCountHeaderRepository;
        this.codeRuleBuilder = codeRuleBuilder;
        this.invCountLineService = invCountLineService;
        this.lovAdapter = lovAdapter;
    }

    @Override
    public Page<InvCountHeader> selectList(PageRequest pageRequest, InvCountHeader invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {

        Map<String, List<InvCountLine>> countLineMap = new HashMap<>();

        for (InvCountHeaderDTO header : invCountHeaders) {
            if (header.getCountHeaderId() == null) {
                String countNumber = generateCountNumber();
                header.setCountStatus(Constants.InvCountHeader.DEFAULT_COUNT_STATUS);
                header.setCountNumber(countNumber);
                header.setDelFlag(BaseConstants.Flag.NO);
                countLineMap.put(countNumber, header.getInvCountLineList());
            } else {
                countLineMap.put(header.getCountNumber(), header.getInvCountLineList());
            }
        }

        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        List<InvCountHeader> insertRes = invCountHeaderRepository.batchInsertSelective(insertList);
        List<InvCountHeader> updateRes = invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);

//        invCountHeaderRepository.batchUpdateOptional(updateList, )

        List<InvCountLine> countLines = new ArrayList<>();

        insertRes.forEach(header -> {
            List<InvCountLine> invCountLines = countLineMap.get(header.getCountNumber());
            invCountLines.forEach(line -> line.setCountHeaderId(header.getCountHeaderId()));
            countLines.addAll(invCountLines);
        });

        updateRes.forEach(header -> countLines.addAll(countLineMap.get(header.getCountNumber())));

        invCountLineService.saveData(countLines);

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

