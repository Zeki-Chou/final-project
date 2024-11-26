package com.hand.demo.domain.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hand.demo.api.dto.ValidateHeaderSave;
import com.hand.demo.api.dto.ValidateOrderExecute;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.choerodon.mybatis.domain.AuditDomain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * (InvCountHeader)实体类
 *
 * @author
 * @since 2024-11-25 10:19:43
 */

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "fexam_inv_count_header")
public class InvCountHeader extends AuditDomain {
    private static final long serialVersionUID = -33867401055306919L;

    public static final String FIELD_COUNT_HEADER_ID = "countHeaderId";
    public static final String FIELD_APPROVED_TIME = "approvedTime";
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
    public static final String FIELD_COMPANY_ID = "companyId";
    public static final String FIELD_COUNT_DIMENSION = "countDimension";
    public static final String FIELD_COUNT_MODE = "countMode";
    public static final String FIELD_COUNT_NUMBER = "countNumber";
    public static final String FIELD_COUNT_STATUS = "countStatus";
    public static final String FIELD_COUNT_TIME_STR = "countTimeStr";
    public static final String FIELD_COUNT_TYPE = "countType";
    public static final String FIELD_COUNTER_IDS = "counterIds";
    public static final String FIELD_DEL_FLAG = "delFlag";
    public static final String FIELD_DEPARTMENT_ID = "departmentId";
    public static final String FIELD_REASON = "reason";
    public static final String FIELD_RELATED_WMS_ORDER_CODE = "relatedWmsOrderCode";
    public static final String FIELD_REMARK = "remark";
    public static final String FIELD_SNAPSHOT_BATCH_IDS = "snapshotBatchIds";
    public static final String FIELD_SNAPSHOT_MATERIAL_IDS = "snapshotMaterialIds";
    public static final String FIELD_SOURCE_CODE = "sourceCode";
    public static final String FIELD_SOURCE_ID = "sourceId";
    public static final String FIELD_SOURCE_SYSTEM = "sourceSystem";
    public static final String FIELD_SUPERVISOR_IDS = "supervisorIds";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_WAREHOUSE_ID = "warehouseId";
    public static final String FIELD_WORKFLOW_ID = "workflowId";

    @ApiModelProperty(hidden = true)
    @Id
    @GeneratedValue
    private Long countHeaderId;

    @ApiModelProperty(hidden = true)
    private Date approvedTime;

    @ApiModelProperty(hidden = true)
    private String attribute1;

    @ApiModelProperty(hidden = true)
    private String attribute10;

    @ApiModelProperty(hidden = true)
    private String attribute11;

    @ApiModelProperty(hidden = true)
    private String attribute12;

    @ApiModelProperty(hidden = true)
    private String attribute13;

    @ApiModelProperty(hidden = true)
    private String attribute14;

    @ApiModelProperty(hidden = true)
    private String attribute15;

    @ApiModelProperty(hidden = true)
    private String attribute2;

    @ApiModelProperty(hidden = true)
    private String attribute3;

    @ApiModelProperty(hidden = true)
    private String attribute4;

    @ApiModelProperty(hidden = true)
    private String attribute5;

    @ApiModelProperty(hidden = true)
    private String attribute6;

    @ApiModelProperty(hidden = true)
    private String attribute7;

    @ApiModelProperty(hidden = true)
    private String attribute8;

    @ApiModelProperty(hidden = true)
    private String attribute9;

    @ApiModelProperty(hidden = true)
    private String attributeCategory;

    @ApiModelProperty(hidden = true)
    @NotBlank(groups = {ValidateHeaderSave.class, ValidateOrderExecute.class})
    private Long companyId;

    @ApiModelProperty(value = "", required = true)
    @NotBlank(groups = {ValidateOrderExecute.class})
    private String countDimension;

    @ApiModelProperty(value = "", required = true)
    @NotBlank(groups = {ValidateOrderExecute.class})
    private String countMode;

    @ApiModelProperty(value = "", required = true)
    @NotBlank
    private String countNumber;

    @ApiModelProperty(value = "", required = true)
    @NotBlank(groups = {ValidateHeaderSave.class, ValidateOrderExecute.class})
    private String countStatus;

    @ApiModelProperty(hidden = true)
    private String countTimeStr;

    @ApiModelProperty(value = "", required = true)
    @NotBlank(groups = {ValidateOrderExecute.class})
    private String countType;

    @ApiModelProperty(hidden = true)
    @NotBlank(groups = {ValidateHeaderSave.class, ValidateOrderExecute.class})
    private Object counterIds;

    @ApiModelProperty(hidden = true)
    private Integer delFlag;

    @ApiModelProperty(hidden = true)
    private Long departmentId;

    @ApiModelProperty(hidden = true)
    private String reason;

    @ApiModelProperty(hidden = true)
    private String relatedWmsOrderCode;

    @ApiModelProperty(hidden = true)
    private String remark;

    @ApiModelProperty(hidden = true)
    private Object snapshotBatchIds;

    @ApiModelProperty(hidden = true)
    private Object snapshotMaterialIds;

    @ApiModelProperty(hidden = true)
    private String sourceCode;

    @ApiModelProperty(hidden = true)
    private Long sourceId;

    @ApiModelProperty(hidden = true)
    private String sourceSystem;

    @ApiModelProperty(hidden = true)
    @NotBlank(groups = ValidateHeaderSave.class)
    private Object supervisorIds;

    @ApiModelProperty(value = "", required = true, hidden = true)
    @NotBlank(groups = {ValidateHeaderSave.class, ValidateOrderExecute.class})
    private Long tenantId;

    @ApiModelProperty(hidden = true)
    @NotBlank(groups = {ValidateHeaderSave.class, ValidateOrderExecute.class})
    private Long warehouseId;

    @ApiModelProperty(hidden = true)
    private Long workflowId;
}

