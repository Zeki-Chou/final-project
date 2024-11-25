package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.infra.constant.InvCountHeaderConstant;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author
 * @since 2024-11-25 08:22:37
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private LovAdapter lovAdapter;

    @Autowired
    private IamRemoteService iamRemoteService;

    String remoteResponse = iamRemoteService.selectSelf().getBody();

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public void saveData(List<InvCountHeaderDTO> invCountHeaders) {
        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<LovValueDTO> validStatusList = lovAdapter.queryLovValue(InvCountHeaderConstant.INV_COUNTING_COUNT_STATUS,
                Long.valueOf(BaseConstants.DEFAULT_TENANT_ID));

        List<String> validStatuses = validStatusList.stream()
                .map(LovValueDTO::getValue)
                .collect(Collectors.toList());

        StringBuilder errorMessages = new StringBuilder();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();

        for (InvCountHeaderDTO data : invCountHeaderDTOS) {
            StringBuilder lineError = new StringBuilder();
            JSONObject jsonObject = new JSONObject(remoteResponse);

            if (data.getCountHeaderId() != null) {
                if (!validStatuses.contains(data.getCountStatus())) {
                    data.setErrMsg(String.valueOf(lineError.append("Data status allowed: only draft, in counting," +
                            " rejected, and withdrawn status can be modified (").append(data.getCountHeaderId()).append("), ")));
                }

                if (Objects.equals(data.getCountStatus(), "DRAFT") && data.getCreatedBy() != jsonObject.getLong("id")) {
                    data.setErrMsg(String.valueOf(lineError.append("Document in draft status can only be modified " +
                            "by the document creator. (").append(data.getCountHeaderId()).append("), ")));
                }

                if (!Objects.equals(data.getCountStatus(), "DRAFT")) {
                    if (data.getRelatedWmsOrderCode() != null && !Arrays.asList(data.getSupervisorIds().split(","))
                            .contains(String.valueOf(jsonObject.getLong("id")))) {
                        data.setErrMsg(String.valueOf(
                                lineError.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate. (")
                                        .append(data.getCountHeaderId()).append("), ")
                        ));
                    }
                    if (!Arrays.asList(data.getSupervisorIds().split(","))
                            .contains(String.valueOf(jsonObject.getLong("id"))) ||
                            !Arrays.asList(data.getCounterIds().split(","))
                            .contains(String.valueOf(jsonObject.getLong("id"))) ||
                            jsonObject.getLong("id") != data.getCreatedBy()
                    ) {
                        data.setErrMsg(
                                String.valueOf(lineError.append("Only the document creator, counter, and supervisor can " +
                                        "modify the document for the status of in counting, rejected, withdrawn.")
                                        .append(data.getCountHeaderId()).append("), "))
                        );
                    }
                }


                if (lineError.length() > 0) {
                    errorMessages.append("Line num: ").append(data.getCountHeaderId()).append(", ")
                            .append(lineError).append("\n");
                }
            }

        }

        invCountInfoDTO.setListErrMsg(
                invCountHeaderDTOS.stream()
                        .filter(header -> header.getErrMsg() != null)
                        .collect(Collectors.toList())
        );

        invCountInfoDTO.setErrMsg(errorMessages.toString());

        if (errorMessages.length() == 0 ) {
            invCountInfoDTO.setSuccessMsg("Success");
        }

        return invCountInfoDTO;
    }
}

