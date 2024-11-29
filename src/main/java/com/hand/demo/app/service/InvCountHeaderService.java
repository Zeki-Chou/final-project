package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author Allan
 * @since 2024-11-25 08:19:18
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaders);

    /**
     * 保存数据
     *
     * @param invCountHeaders 数据
     */
    List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders);

    /**
     * @param invCountHeaderDTOS list of header dto
     * @return count info dto
     */
    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);

    /**
     * @param invCountHeaderDTOS list of header dto
     * @return invoice info dto
     */
    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);

    /**
     * @param countHeaderId count header id
     * @return header dto
     */
    InvCountHeaderDTO detail(Long countHeaderId);

    /**
     * @param invCountHeaderDTOList
     * @return
     */
    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaderDTOList);

    /**
     * @param invCountHeaderList
     * @return
     */
    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaderList);

    /**
     * @param invCountHeaderList
     * @return
     */
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList);

    /**
     * @param invCountHeaderDTO
     * @return
     */
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO invCountHeaderDTO);

    /**
     * @param countHeaders
     * @return
     */
    InvCountInfoDTO  submitCheck(List<InvCountHeaderDTO> countHeaders);

    List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaderList);
}

