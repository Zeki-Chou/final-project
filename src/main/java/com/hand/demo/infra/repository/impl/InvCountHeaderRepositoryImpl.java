package com.hand.demo.infra.repository.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.infra.constant.InvCountHeaderConstants;
import com.hand.demo.infra.mapper.InvCountLineMapper;
import io.choerodon.core.oauth.DetailsHelper;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.mapper.InvCountHeaderMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvCountHeader)资源库
 *
 * @author
 * @since 2024-11-25 09:59:38
 */
@Component
public class InvCountHeaderRepositoryImpl extends BaseRepositoryImpl<InvCountHeaderDTO> implements InvCountHeaderRepository {
    @Resource
    private InvCountHeaderMapper invCountHeaderMapper;
    @Resource
    private InvCountLineMapper invCountLineMapper;
    @Autowired
    private IamRemoteService iamRemoteService;
    @Override
    public List<InvCountHeaderDTO> selectList(InvCountHeaderDTO invCountHeaderDTO) {
        String userJson = iamRemoteService.selectSelf().getBody();
        JSONObject jsonObject= JSON.parseObject(userJson);
        Boolean isTenantAdmin = (Boolean) jsonObject.getOrDefault(InvCountHeaderConstants.IamRemoteService.TENANT_ADMIN_FLAG,null);
        if(isTenantAdmin == null){
            isTenantAdmin = (Boolean) jsonObject.getOrDefault(InvCountHeaderConstants.IamRemoteService.TENANT_SUPER_ADMIN_FLAG,null);
        }
        invCountHeaderDTO.setIsTenantAdmin(isTenantAdmin);
        invCountHeaderDTO.setTenantId(DetailsHelper.getUserDetails().getTenantId());

        List<InvCountHeaderDTO> resultInvCountHeaderDTOS = invCountHeaderMapper.selectList(invCountHeaderDTO);
        for (InvCountHeaderDTO resultInvCountHeaderDTO:resultInvCountHeaderDTOS){
            InvCountLineDTO lineListParam = new InvCountLineDTO();
            lineListParam.setCountHeaderId(resultInvCountHeaderDTO.getCountHeaderId());
            lineListParam.setSupervisorIds(resultInvCountHeaderDTO.getSupervisorIds());
            lineListParam.setIsTenantAdmin(invCountHeaderDTO.getIsTenantAdmin());
            List<InvCountLineDTO> invCountLineDTOS = invCountLineMapper.selectList(lineListParam);
            resultInvCountHeaderDTO.setInvCountLineDTOList(invCountLineDTOS);
        }

        return resultInvCountHeaderDTOS;
    }

    @Override
    public InvCountHeaderDTO selectByPrimary(Long countHeaderId) {
        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        invCountHeaderDTO.setCountHeaderId(countHeaderId);
        List<InvCountHeaderDTO> invCountHeaderDTOS = selectList(invCountHeaderDTO);
        if (invCountHeaderDTOS.size() == 0) {
            return null;
        }
        return invCountHeaderDTOS.get(0);
    }

}

