package com.hand.demo.infra.constant;

import java.util.Arrays;
import java.util.List;

public class InvCountHeaderConstants {
    private InvCountHeaderConstants() {}
    public static final String INV_COUNT_HEADER_COUNT_STATUS = "INV.COUNTING.COUNT_STATUS";
    public static final List<String> HEADER_COUNT_DESIRED_STATUSES = Arrays.asList("INCOUNTING", "REJECTED", "WITHDRAWN");
    public static final String COUNT_STATUS_DRAFT = "DRAFT";
}
