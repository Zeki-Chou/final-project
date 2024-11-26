package com.hand.demo.domain.entity;

import java.util.Date;

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
 * 用户(User)实体类
 *
 * @author
 * @since 2024-11-25 10:48:01
 */

@Getter
@Setter
@ApiModel("用户")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "iam_user")
public class User extends AuditDomain {
    private static final long serialVersionUID = -64071336287886437L;

    public static final String FIELD_ID = "id";
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
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_HASH_PASSWORD = "hashPassword";
    public static final String FIELD_IMAGE_URL = "imageUrl";
    public static final String FIELD_INTERNATIONAL_TEL_CODE = "internationalTelCode";
    public static final String FIELD_IS_ADMIN = "isAdmin";
    public static final String FIELD_IS_ENABLED = "isEnabled";
    public static final String FIELD_IS_LDAP = "isLdap";
    public static final String FIELD_IS_LOCKED = "isLocked";
    public static final String FIELD_LANGUAGE = "language";
    public static final String FIELD_LAST_LOGIN_AT = "lastLoginAt";
    public static final String FIELD_LAST_PASSWORD_UPDATED_AT = "lastPasswordUpdatedAt";
    public static final String FIELD_LOCKED_UNTIL_AT = "lockedUntilAt";
    public static final String FIELD_LOGIN_NAME = "loginName";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_PASSWORD_ATTEMPT = "passwordAttempt";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_PROFILE_PHOTO = "profilePhoto";
    public static final String FIELD_QUICK_INDEX = "quickIndex";
    public static final String FIELD_REAL_NAME = "realName";
    public static final String FIELD_TIME_ZONE = "timeZone";
    public static final String FIELD_USER_TYPE = "userType";
    public static final String FIELD_UUID = "uuid";

    @Id
    @GeneratedValue
    private Long id;

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

    private String email;

    @ApiModelProperty(value = "Hash后的用户密码")
    private String hashPassword;

    @ApiModelProperty(value = "用户头像地址")
    private String imageUrl;

    @ApiModelProperty(value = "国际电话区号。")
    private String internationalTelCode;

    @ApiModelProperty(value = "是否为管理员用户。1表示是，0表示不是")
    private Integer isAdmin;

    @ApiModelProperty(value = "用户是否启用。1启用，0未启用", required = true)
    @NotNull
    private Integer isEnabled;

    @ApiModelProperty(value = "是否是LDAP来源。1是，0不是")
    private Integer isLdap;

    @ApiModelProperty(value = "是否锁定账户", required = true)
    @NotNull
    private Integer isLocked;

    private String language;

    @ApiModelProperty(value = "上一次登陆时间")
    private Date lastLoginAt;

    @ApiModelProperty(value = "上一次密码更新时间", required = true)
    @NotNull
    private Date lastPasswordUpdatedAt;

    @ApiModelProperty(value = "锁定账户截止时间")
    private Date lockedUntilAt;

    @ApiModelProperty(value = "用户名", required = true)
    @NotBlank
    private String loginName;

    @ApiModelProperty(value = "组织ID", required = true)
    @NotNull
    private Long organizationId;

    @ApiModelProperty(value = "密码输错累积次数")
    private Integer passwordAttempt;

    private String phone;

    @ApiModelProperty(value = "用户二进制头像")
    private Object profilePhoto;

    @ApiModelProperty(value = "快速索引")
    private String quickIndex;

    @ApiModelProperty(value = "用户真实姓名")
    private String realName;

    @ApiModelProperty(value = "时区", required = true)
    @NotBlank
    private String timeZone;

    @ApiModelProperty(value = "用户类型(P/C)，平台用户/C端用户，默认P", required = true)
    @NotBlank
    private String userType;

    @ApiModelProperty(value = "uuid，主要用于ldap同步")
    private String uuid;


}

