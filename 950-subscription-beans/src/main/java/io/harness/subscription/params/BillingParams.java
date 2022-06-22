package io.harness.subscription.params;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BillingParams {
    private String customerId;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String creditCardId;
}
