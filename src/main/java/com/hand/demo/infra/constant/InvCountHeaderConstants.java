package com.hand.demo.infra.constant;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.InvCountHeader;

public class InvCountHeaderConstants {
    private InvCountHeaderConstants() {
    }
    public static class Workflow {
        public static class Submit {
            public final static String FLOW_KEY = "INV_COUNT60_RESULT_SUBMIT";
            public final static String DIMENSION = "EMPLOYEE";
            public final static String STARTER = "47360";
            public final static String VAR_DEPARTMENT_CODE = "departmentCode";

        }
    }

    public static class Profile{
        public static class CountingWorkflow{
            public final static String  NAME =  "FEXAM95.INV.COUNTING.ISWORKFLOW";
        }
    }
    public static class InterfaceSDK{
        public static class WMSCounting{
            public final static String  NAMESPACE ="HZERO";
            public final static String  SERVER_CODE ="FEXAM_WMS";
            public final static String INTERFACE_CODE="fexam-wms-api.thirdAddCounting";
            public final static String RESPONSE_KEY_STATUS = "returnStatus";
            public final static String RESPONSE_KEY_MSG = "returnMsg";
            public final static String RESPONSE_KEY_CODE = "code";
            public final static String RESPONSE_VALUE_STATUS_SUCCESS = "S";

        }
    }
    public static class Lov {
        public static class Status {
            public final static String CODE = "INV.COUNTING.COUNT_STATUS";
        }
        public static class Dimension {
            public final static String CODE = "INV.COUNTING.COUNT_DIMENSION";
        }
        public static class Type {
            public final static String CODE = "INV.COUNTING.COUNT_TYPE";
        }
        public static class Mode {
            public final static String CODE = "INV.COUNTING.COUNT_MODE";
        }
    }
    public static class Value {
        public static class DelFlag {
            public final static Integer DEFAULT =DelFlag.EXIST;
            public final static Integer EXIST = 0;
        }
        public static class CountStatus {
            public final static String DEFAULT = CountStatus.DRAFT;

            public final static String DRAFT = "DRAFT";
            public final static String IN_COUNTING = "INCOUNTING";
            public final static String PROCESSING = "PROCESSING";
            public final static String REJECTED = "REJECTED";
            public final static String WITHDRAWN = "WITHDRAWN";
            public final static String CONFIRMED = "CONFIRMED";
            public final static String APPROVED = "APPROVED";

        }

    }
    public static class CodeRule {
        public static class CountNumber {
            public final static String CODE = "INV.COUNTING61.COUNT_NUMBER";
            public final static String CUSTOM_SEGMENT_KEY = "customSegment";

        }
    }
    public static class UpdateOptional{
        public static class Save {
            public final static String[] DRAFT ={
                    InvCountHeaderDTO.FIELD_TENANT_ID,
                    InvCountHeaderDTO.FIELD_COMPANY_ID,
                    InvCountHeaderDTO.FIELD_DEPARTMENT_ID,
                    InvCountHeaderDTO.FIELD_WAREHOUSE_ID,
                    InvCountHeaderDTO.FIELD_COUNT_DIMENSION,
                    InvCountHeaderDTO.FIELD_COUNT_TYPE,
                    InvCountHeaderDTO.FIELD_COUNT_MODE,
                    InvCountHeaderDTO.FIELD_COUNT_TIME_STR,
                    InvCountHeaderDTO.FIELD_COUNTER_IDS,
                    InvCountHeaderDTO.FIELD_SUPERVISOR_IDS,
                    InvCountHeaderDTO.FIELD_SNAPSHOT_MATERIAL_IDS,
                    InvCountHeaderDTO.FIELD_SNAPSHOT_BATCH_IDS,
                    InvCountHeaderDTO.FIELD_REMARK,
            };
            public final static  String[] IN_COUNTING = {
                    InvCountHeaderDTO.FIELD_TENANT_ID,
                    InvCountHeaderDTO.FIELD_REMARK,
                    InvCountHeaderDTO.FIELD_REASON,
            };
            public final static String[] REJECTED={
                    InvCountHeaderDTO.FIELD_TENANT_ID,
                    InvCountHeaderDTO.FIELD_REASON,
            };
            public final static String[] WITHDRAW = {
                    InvCountHeaderDTO.FIELD_TENANT_ID,
            };
        }
        public static class Execute {
            public final static String[] EXECUTE = {
                    InvCountHeaderDTO.FIELD_COUNT_STATUS,
            };
        }

        public static class Sync {
            public final static String[] WMS = {
                    InvCountHeaderDTO.FIELD_RELATED_WMS_ORDER_CODE,
            };
        }
        public static class Submit {
            public final static String[] SUBMIT = {
                    InvCountHeaderDTO.FIELD_RELATED_WMS_ORDER_CODE,
            };
            public final static String[] APPROVE = {
                    InvCountHeader.FIELD_WORKFLOW_ID,
                    InvCountHeader.FIELD_COUNT_STATUS,
                    InvCountHeader.FIELD_APPROVED_TIME
            };
        }
    }
}