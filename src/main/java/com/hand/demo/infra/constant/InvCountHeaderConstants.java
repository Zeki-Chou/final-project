package com.hand.demo.infra.constant;

import java.util.Arrays;
import java.util.List;

public class InvCountHeaderConstants {
    private InvCountHeaderConstants() {}
    public static final String INV_COUNT_HEADER_COUNT_STATUS = "INV.COUNTING.COUNT_STATUS";
    public static final List<String> HEADER_COUNT_DESIRED_STATUSES = Arrays.asList("INCOUNTING", "REJECTED", "WITHDRAWN");
    public static final String COUNT_STATUS_DRAFT = "DRAFT";

    public static final String COUNT_STATUS_INCOUNTING = "INCOUNTING";

    public static final String COUNT_STATUS_REJECTED = "REJECTED";

    public static final String COUNT_NUMBER_CODE_RULE = "INV.COUNTING61.COUNT_NUMBER";
}
