package com.hand.demo.domain.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.choerodon.mybatis.domain.AuditDomain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 租户信息(Tenant)实体类
 *
 * @author
 * @since 2024-12-04 09:44:01
 */

@Getter
@Setter
@ApiModel("租户信息")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "hpfm_tenant")
public class Tenant extends AuditDomain {
    private static final long serialVersionUID = -82989925845970825L;

    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_ENABLED_FLAG = "enabledFlag";
    public static final String FIELD_EXT_INFO = "extInfo";
    public static final String FIELD_LIMIT_LANGUAGE = "limitLanguage";
    public static final String FIELD_LIMIT_USER_QTY = "limitUserQty";
    public static final String FIELD_TABLE_SPLIT_FLAG = "tableSplitFlag";
    public static final String FIELD_TABLE_SPLIT_SEQ = "tableSplitSeq";
    public static final String FIELD_TENANT_NAME = "tenantName";
    public static final String FIELD_TENANT_NUM = "tenantNum";

    @Id
    @GeneratedValue
    private Long tenantId;

    @ApiModelProperty(value = "是否启用。1启用，0未启用", required = true)
    @NotNull
    private Integer enabledFlag;

    private String extInfo;

    @ApiModelProperty(value = "租户可使用语言，null表示不限制")
    private String limitLanguage;

    @ApiModelProperty(value = "租户下的有效用户数，null表示不限制")
    private Integer limitUserQty;

    @ApiModelProperty(value = "是否分表")
    private Integer tableSplitFlag;

    private Long tableSplitSeq;

    @ApiModelProperty(value = "租户名", required = true)
    @NotBlank
    private String tenantName;

    @ApiModelProperty(value = "租户编码", required = true)
    @NotBlank
    private String tenantNum;


}

