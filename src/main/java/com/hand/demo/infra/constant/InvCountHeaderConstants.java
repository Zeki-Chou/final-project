package com.hand.demo.infra.constant;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;

public class InvCountHeaderConstants {
    private InvCountHeaderConstants() {
    }

    public static class LovCode {
        public final static String STATUS = "INV.COUNTING.COUNT_STATUS";
        public final static String DIMENSION = "INV.COUNTING.COUNT_DIMENSION";
        public final static String TYPE = "INV.COUNTING.COUNT_TYPE";
        public final static String MODE = "INV.COUNTING.COUNT_MODE";
    }

    public static class LovValue {
        public final static String STATUS_DRAFT = "DRAFT";
        public final static String STATUS_IN_COUNTING = "INCOUNTING";
        public final static String STATUS_REJECTED = "REJECTED";
        public final static String STATUS_WITHDRAWN = "WITHDRAWN";
    }
    public static class CodeRule {
        public final static String COUNT_NUMBER = "INV.COUNTING61.COUNT_NUMBER";

    }
    public static class UpdateOptional{
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
    public static class DefaultValue {
        public final static Integer DEL_FLAG = 0;
        public final static String STATUS = LovValue.STATUS_DRAFT;
    }


}