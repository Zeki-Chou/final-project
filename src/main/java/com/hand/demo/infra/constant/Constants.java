package com.hand.demo.infra.constant;

/**
 * Utils
 */
public class Constants {


    public static final String CODE_RULE = "INV.COUNTING57.COUNT_NUMBER";

    public static class Extra {
        public static final String PROG_KEY_STATUS = "wms_sync_status";
        public static final String PROG_KEY_ERR_MSG = "wms_sync_error_message";
    }

    public static class LovCode {
        public static final String COUNT_STATUS = "INV.COUNTING.COUNT_STATUS";
        public static final String COUNT_DIMENSION = "INV.COUNTING.COUNT_DIMENSION";
        public static final String COUNT_TYPE = "INV.COUNTING.COUNT_TYPE";
        public static final String COUNT_MODE = "INV.COUNTING.COUNT_MODE";
    }

    public static class Interface {
        public static final String INTERFACE_NAME_SPACE = "HZERO";
        public static final String INTERFACE_SERVER_CODE = "FEXAM_WMS";
        public static final String INTERFACE_CODE = "fexam-wms-api.thirdAddCounting";
    }

    public static final String COUNT_DRAFT_STATUS = "DRAFT";
    public static final String COUNT_INCOUNTING_STATUS = "INCOUNTING";
    public static final String COUNT_REJECTED_STATUS = "REJECTED";
    public static final String COUNT_WITHDRAWN_STATUS = "WITHDRAWN";

    public static final String COUNT_SKU_DIMENSION = "SKU";
    public static final String COUNT_LOT_DIMENSION = "LOT";

    private Constants() {}


}
