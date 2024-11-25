package com.hand.demo.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.hand.demo.api.controller.v1.DTO.InvCountException;
import com.hand.demo.api.controller.v1.DTO.InvCountHeaderDTO;
import com.hand.demo.api.controller.v1.DTO.InvCountInfoDTO;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.constant.InvCountHeaderConstant;
import com.netflix.discovery.converters.Auto;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.json.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 08:42:19
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Autowired
    LovAdapter lovAdapter;

    @Autowired
    IamRemoteService iamRemoteService;

    @Autowired
    CodeRuleBuilder codeRuleBuilder;

    @Override
    public Page<InvCountHeader> selectList(PageRequest pageRequest, InvCountHeader invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    public InvCountInfoDTO dataValidation (List<InvCountHeaderDTO> invCountHeadersDTO) {
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        List<String> errorMessage = new ArrayList<>();
        List<String> statusValidation1 = new ArrayList<>();
        statusValidation1.add("DRAFT");
        statusValidation1.add("INCOUNTING");
        statusValidation1.add("REJECTED");
        statusValidation1.add("WITHDRAWN");

        List<String> statusValidation2 = new ArrayList<>();
        statusValidation2.add("INCOUNTING");
        statusValidation2.add("REJECTED");
        statusValidation2.add("WITHDRAWN");

        List<InvCountHeaderDTO> updateList = invCountHeadersDTO.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        JSONObject jsonObject = new JSONObject(iamRemoteService.selectSelf().getBody());
        Long userId = jsonObject.getLong("id");

//  count header
        List<Long> headerIds = updateList.stream().map(InvCountHeaderDTO::getCountHeaderId).collect(Collectors.toList());
        String joinHeaderId = String.join(",", headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));

        Map<Long, InvCountHeader> invCountHeaderMap = invCountHeaderRepository.selectByIds(joinHeaderId).stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));

//  warehouse
        Set<Long> warehouseIds = updateList.stream()
                .map(InvCountHeader::getWarehouseId)
                .collect(Collectors.toSet());
        ArrayList<Long> warehouseIdList = new ArrayList<>(warehouseIds);

        String joinWarehouseId = String.join(",", warehouseIdList.stream()
                .map(String::valueOf)
                .collect(Collectors.toList()));

        Map<Long, InvWarehouse> invCountWarehouseMap = invWarehouseRepository.selectByIds(joinWarehouseId).stream()
                .collect(Collectors.toMap(InvWarehouse::getWarehouseId, header -> header));

        for(InvCountHeaderDTO update : updateList) {
            InvCountHeader invCountHeader = invCountHeaderMap.get(update.getCountHeaderId());

            if(!statusValidation1.contains(String.valueOf(invCountHeader.getCountStatus()))) {
                errorMessage.add("error1");
            }

            if ("DRAFT".equals(invCountHeader.getCountStatus()) && !String.valueOf(userId).equals(String.valueOf(invCountHeader.getCreatedBy()))) {
                errorMessage.add("error2");
            }

            if(!statusValidation2.contains(String.valueOf(invCountHeader.getCountStatus()))) {
                InvWarehouse invWarehouse = invCountWarehouseMap.get(update.getWarehouseId());
                if(invWarehouse == null) {
                    invWarehouse.setIsWmsWarehouse(0);
                }

                if (!update.getSupervisorIds().equals(userId.toString()) && invWarehouse.getIsWmsWarehouse() != 0) {
                    errorMessage.add("error3");
                }
            }

            List<String> listOperator = new ArrayList<>();
            listOperator.add(invCountHeader.getCounterIds());
            listOperator.add(invCountHeader.getSupervisorIds());
            listOperator.add(invCountHeader.getCreatedBy().toString());

            if(!listOperator.contains(String.valueOf(userId))) {
                errorMessage.add("error4");
            }
        }

        Set<String> setErrorMessage = new HashSet<>();
        if(errorMessage.contains("error1")) {
            setErrorMessage.add("only draft, in counting, rejected, and withdrawn status can be modified.");
        }
        if(errorMessage.contains("error2")) {
            setErrorMessage.add("Document in draft status can only be modified by the document creator.");
        }
        if(errorMessage.contains("error3")) {
            setErrorMessage.add("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
        }
        if(errorMessage.contains("error4")) {
            setErrorMessage.add("only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn.");
        }

        List<String> errorMessageList = new ArrayList<>(setErrorMessage);
        invCountInfoDTO.setErrorMessage(errorMessageList);
        return invCountInfoDTO;
    }

    @Override
    public List<InvCountHeaderDTO> saveData(List<InvCountHeaderDTO> invCountHeadersDTO) {
//      validation data
        InvCountInfoDTO invCountInfoDTO = dataValidation(invCountHeadersDTO);

        if(invCountInfoDTO.getErrorMessage().size() > 0) {
            throw new CommonException(JSON.toJSONString(invCountInfoDTO));
        }

//      insert
        List<InvCountHeader> insertList = invCountHeadersDTO.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<String> batchCode = codeRuleBuilder.generateCode(insertList.size(), InvCountHeaderConstant.INVOICE_COUNTING, null);

        for (int i = 0; i < insertList.size(); i++) {
            InvCountHeader insert = insertList.get(i);
            InvCountHeader invCountHeader = new InvCountHeader();
            BeanUtils.copyProperties(insert, invCountHeader);

            invCountHeader.setCountNumber(batchCode.get(i));
        }
        invCountHeaderRepository.batchInsertSelective(insertList);

//      update
        List<InvCountHeaderDTO> updateList = invCountHeadersDTO.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        for(InvCountHeaderDTO update : updateList) {

        }

        return invCountHeadersDTO;
//        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
    }
}

