package com.hand.demo.infra.constant;

public class InvCountExtraConstants {
    private InvCountExtraConstants() {}

    public static class Value {

        public static class ProgramKey {
            public final static String STATUS = "wms_sync_status";
            public final static String ERROR_MESSAGE = "wms_sync_error_message";

        }
        public static class ProgramValue {
            public final static String ERROR = "ERROR";
            public final static String SUCCESS = "SUCCESS";
            public final static String SKIP = "SKIP";
        }

        public static class EnabledFlag {
            public final static Integer DEFAULT = EnabledFlag.ENABLED;

            public final static Integer ENABLED = 1;

        }


    }
}
