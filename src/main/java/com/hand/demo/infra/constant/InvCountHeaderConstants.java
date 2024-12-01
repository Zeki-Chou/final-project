package com.hand.demo.infra.constant;

import io.choerodon.core.oauth.DetailsHelper;

import java.util.Arrays;
import java.util.List;

public class InvCountHeaderConstants {
    private InvCountHeaderConstants() {}
    public static final Long EMPLOYEE_ID = 47355L;

    // LOV
    public static final String INV_COUNT_HEADER_COUNT_STATUS = "INV.COUNTING.COUNT_STATUS";
    public static final String INV_COUNT_HEADER_COUNT_DIMENSION = "INV.COUNTING.COUNT_DIMENSION";
    public static final String INV_COUNT_HEADER_COUNT_TYPE = "INV.COUNTING.COUNT_TYPE";
    public static final String INV_COUNT_HEADER_COUNT_MODE = "INV.COUNTING.COUNT_MODE";

    public static final List<String> HEADER_COUNT_DESIRED_STATUSES = Arrays.asList("INCOUNTING", "REJECTED", "WITHDRAWN");
    public static final List<String> HEADER_COUNT_DESIRED_SUBMISSION_STATUSES = Arrays.asList("INCOUNTING", "PROCESSING", "REJECTED", "WITHDRAWN");

    public static final String COUNT_STATUS_DRAFT = "DRAFT";

    public static final String COUNT_STATUS_INCOUNTING = "INCOUNTING";

    public static final String COUNT_STATUS_REJECTED = "REJECTED";

    public static final String COUNT_STATUS_CONFIRMED = "CONFIRMED";

    public static final String COUNT_NUMBER_CODE_RULE = "INV.COUNTING61.COUNT_NUMBER";

    public static final String WMS_SYNC_PROGRAM_KEY_STATUS = "wms_sync_status";
    public static final String WMS_SYNC_PROGRAM_KEY_ERROR_MSG = "wms_sync_error_message";

    public static final String EXTERNAL_WMS_SERVICE_NAMESPACE = "HZERO";

    public static final String EXTERNAL_WMS_SERVICE_SERVER_CODE = "FEXAM_WMS";

    public static final String EXTERNAL_WMS_SERVICE_INTERFACE_CODE = "fexam-wms-api.thirdAddCounting";

    // Submit Workflow
    public static final String WORKFLOW_PROFILE_CLIENT = "FEXAM55.INV.COUNTING.ISWORKFLO";

    public static final String FLOW_KEY = "INV_COUNT55_RESULT_SUBMIT";

    public static final String DIMENSION = "EMPLOYEE";

    public static final String DEPARTMENT_CODE = "departmentCode";
}
