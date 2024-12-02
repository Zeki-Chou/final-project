package com.hand.demo.infra.constant;

/**
 * Utils
 */
public class Constants {

    private Constants() {}

    public static class Iam {

        private Iam() {}

        public static final String FIELD_ID = "id";
    }

    public static class Workflow {
        private Workflow() {}

        public static final String FLOW_KEY = "INV_COUNT59_RESULT_SUBMIT";
        public static final String DIMENSION = "EMPLOYEE";
        public static final String STARTER = "47359";

        public static final String DEPARTMENT_FIELD = "departmentCode";
    }

    public static class ExternalService {

        private ExternalService() {}

        public static final String NAMESPACE = "HZERO";
        public static final String SERVER_CODE = "FEXAM_WMS";
        public static final String INTERFACE_CODE = "fexam-wms-api.thirdAddCounting";

        public static final String RESULT_STATUS_FIELD = "returnStatus";
        public static final String RETURN_MESSAGE_FIELD = "returnMsg";
        public static final String CODE_FIELD = "code";

        public static final String RESULT_STATUS_SUCCESS = "S";
        public static final String RESULT_STATUS_ERROR = "E";

        public static final String ERR_INVALID_WMS = "The current warehouse is not a WMS warehouse, operations are not allowed";
        public static final String ERR_COUNT_LINE_INCONSISTENT = "The counting order line data is inconsistent with the INV system, please check the data";
        public static final String SUCCESS_MSG = "Successfully syncing the data into local";
    }

    public static class CodeBuilder {

        private CodeBuilder() {}

        public static final String FIELD_CUSTOM_SEGMENT = "customSegment";
    }

    public static class InvCountHeader {

        private InvCountHeader() {}

        public static final String UPDATE_STATUS_INVALID = "only draft, in counting, rejected, and withdrawn status can be modified";
        public static final String UPDATE_ACCESS_INVALID = "Document in draft status can only be modified by the document creator";
        public static final String WAREHOUSE_SUPERVISOR_INVALID = "The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate";
        public static final String ACCESS_UPDATE_STATUS_INVALID = "only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn";

        public static final String SUBMIT_STATUS_INVALID = "The operation is allowed only when the status in in counting, processing, rejected, withdrawn.";
        public static final String SUBMIT_USER_INVALID = "Only the current login user is the supervisor can submit document.";
        public static final String SUBMIT_COUNT_QTY_INVALID = "There are data rows with empty count quantity. Please check the data.";
        public static final String SUBMIT_REASON_INVALID = "there is a difference in counting, the reason field must be entered.";

        public static final String STATUS_LOV_CODE = "INV.COUNTING.COUNT_STATUS";
        public static final String COUNT_DIMENSION_LOV_CODE = "INV.COUNTING.COUNT_DIMENSION";
        public static final String COUNT_TYPE_LOV_CODE = "INV.COUNTING.COUNT_TYPE";
        public static final String COUNT_MODE_LOV_CODE = "INV.COUNTING.COUNT_MODE";

        public static final String CODE_RULE = "INV.COUNTING59.COUNT_NUMBER";

        public static final String COUNTING_WORKFLOW = "FEXAM55.INV.COUNTING.ISWORKFLO";
    }

    public static class InvWarehouse {
        private InvWarehouse() {}

        public static final String WAREHOUSE_NOT_FOUND = "warehouse not found";
    }

    public static class InvCountExtra {

        private InvCountExtra() {}

        public static final String PROGRAM_KEY_WMS_SYNC_STATUS = "wms_sync_status";
        public static final String PROGRAM_KEY_WMS_SYNC_ERR_MSG ="wms_sync_error_message";
    }

}
