package com.hand.demo.infra.constant;

import com.hand.demo.api.dto.InvCountLineDTO;

public class InvCountLineConstants {
    private InvCountLineConstants() {
    }
    public static class UpdateOptional {
        public  static final String[] DRAFT={
                InvCountLineDTO.FIELD_TENANT_ID
        };
        public static final String[] IN_COUNTING={
                InvCountLineDTO.FIELD_TENANT_ID,
                InvCountLineDTO.FIELD_UNIT_QTY,
                InvCountLineDTO.FIELD_UNIT_DIFF_QTY,
                InvCountLineDTO.FIELD_COUNTER_IDS,
                InvCountLineDTO.FIELD_REMARK
        };
    }
}
