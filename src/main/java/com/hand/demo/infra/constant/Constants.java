package com.hand.demo.infra.constant;

/**
 * Utils
 */
public class Constants {

    private Constants() {}

    public static class InvCountHeader {
        public static final String DEFAULT_COUNT_STATUS = "DRAFT";
        public static final String INCOUNTING_VALUE = "INCOUNTING";
        public static final String PROCESSING_VALUE = "PROCESSING";
        public static final String WITHDRAWN_VALUE = "WITHDRAWN";
        public static final String REJECTED_VALUE = "Rejected";

        public static final String UPDATE_STATUS_INVALID = "only draft, in counting, rejected, and withdrawn status can be modified";
        public static final String UPDATE_ACCESS_INVALID = "Document in draft status can only be modified by the document creator";
        public static final String WAREHOUSE_SUPERVISOR_INVALID = "The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate";
        public static final String ACCESS_UPDATE_STATUS_INVALID = "only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn";

        public static final String STATUS_LOV_CODE = "INV.COUNTING.COUNT_STATUS";

        public static final String CODE_RULE = "INV.COUNTING59.COUNT_NUMBER";
    }

}
