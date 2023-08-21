/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.jvnet.hk2.annotations.Optional;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerDTO {
  private static final String COMPANY_NAME_REGEX = "^[a-zA-Z0-9 \\-.,'&#/()@!]+$";
  private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}$";

  @Pattern(regexp = EMAIL_REGEX, message = "Email must be valid.") @Size(max = 254) private String billingEmail;
  @Pattern(regexp = COMPANY_NAME_REGEX) @Size(max = 46) private String companyName;
  @Valid private AddressDto address;
  @Optional private String defaultPaymentMethod;
}
