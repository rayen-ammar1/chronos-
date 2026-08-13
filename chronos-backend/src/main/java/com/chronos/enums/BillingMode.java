package com.chronos.enums;

public enum BillingMode {
    FIXED_PRICE,        // Forfait in French source
    TIME_AND_MATERIAL,  // Régie in French source
    NOTAPPLICABLE,      // value observed in Employee Time CSV (exact casing)
    NOT_BILLABLE        // internal activities
}
