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
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * (InvCountExtra)实体类
 *
 * @author
 * @since 2024-11-28 14:24:52
 */

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "fexam_inv_count_extra")
@Accessors(chain = true)
public class InvCountExtra extends AuditDomain {
    private static final long serialVersionUID = -64012834494305974L;

    public static final String FIELD_EXTRAINFOID = "extrainfoid";
    public static final String FIELD_ATTRIBUTE1 = "attribute1";
    public static final String FIELD_ATTRIBUTE10 = "attribute10";
    public static final String FIELD_ATTRIBUTE11 = "attribute11";
    public static final String FIELD_ATTRIBUTE12 = "attribute12";
    public static final String FIELD_ATTRIBUTE13 = "attribute13";
    public static final String FIELD_ATTRIBUTE14 = "attribute14";
    public static final String FIELD_ATTRIBUTE15 = "attribute15";
    public static final String FIELD_ATTRIBUTE2 = "attribute2";
    public static final String FIELD_ATTRIBUTE3 = "attribute3";
    public static final String FIELD_ATTRIBUTE4 = "attribute4";
    public static final String FIELD_ATTRIBUTE5 = "attribute5";
    public static final String FIELD_ATTRIBUTE6 = "attribute6";
    public static final String FIELD_ATTRIBUTE7 = "attribute7";
    public static final String FIELD_ATTRIBUTE8 = "attribute8";
    public static final String FIELD_ATTRIBUTE9 = "attribute9";
    public static final String FIELD_ATTRIBUTE_CATEGORY = "attributeCategory";
    public static final String FIELD_ENABLEDFLAG = "enabledflag";
    public static final String FIELD_PROGRAMKEY = "programkey";
    public static final String FIELD_PROGRAMVALUE = "programvalue";
    public static final String FIELD_REMARK = "remark";
    public static final String FIELD_SOURCEID = "sourceid";
    public static final String FIELD_TENANTID = "tenantid";

    @Id
    @GeneratedValue
    private Long extrainfoid;

    private String attribute1;

    private String attribute10;

    private String attribute11;

    private String attribute12;

    private String attribute13;

    private String attribute14;

    private String attribute15;

    private String attribute2;

    private String attribute3;

    private String attribute4;

    private String attribute5;

    private String attribute6;

    private String attribute7;

    private String attribute8;

    private String attribute9;

    private String attributeCategory;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Integer enabledflag;

    @ApiModelProperty(value = "", required = true)
    @NotBlank
    private String programkey;

    @ApiModelProperty(value = "", required = true)
    @NotBlank
    private String programvalue;

    private String remark;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Long sourceid;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Long tenantid;


}

