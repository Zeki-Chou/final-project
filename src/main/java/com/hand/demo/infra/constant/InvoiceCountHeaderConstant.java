package com.hand.demo.infra.constant;

public class InvoiceCountHeaderConstant {
    public static class LovCode {
        public static final String COUNT_STATUS = "INV.COUNTING.COUNT_STATUS";
        public static final String COUNT_DIMENSION = "INV.COUNTING.COUNT_DIMENSION";
        public static final String COUNT_TYPE = "INV.COUNTING.COUNT_TYPE";
        public static final String COUNT_MODE = "INV.COUNTING.COUNT_MODE";
    }

    public static class CodeRule {
        public static final String CODE_RULE_HEADER_NUMBER = "INV.COUNTING61.COUNT_NUMBER";
    }

    public static class ExternalInterface {
        public static final String INTERFACE_NAME_SPACE = "HZERO";
        public static final String INTERFACE_SERVER_CODE = "FEXAM_WMS";
        public static final String INTERFACE_CODE = "fexam-wms-api.thirdAddCounting";
    }

    public static  class Extra {
        public static final String PROGRAM_KEY_STATUS = "wms_sync_status";
        public static final String PROGRAM_KEY_ERROR_MSG = "wms_sync_error_message";
    }

    public static class Workflow {
        public static final String FLOW_KEY = "INV_COUNT61_RESULT_SUBMIT";
    }

    public InvoiceCountHeaderConstant(){}
}
