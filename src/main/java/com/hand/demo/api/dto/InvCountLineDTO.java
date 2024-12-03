package com.hand.demo.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hand.demo.domain.entity.InvCountLine;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "fexam_inv_count_line")
public class InvCountLineDTO extends InvCountLine {
    @Transient
    private String materialName;
    @Transient
    private String materialCode;
    @Transient
    private String batchCode;
    @Transient
    private List<UserDTO> counterList;
    @Transient
    private String counterNamesString;

}
