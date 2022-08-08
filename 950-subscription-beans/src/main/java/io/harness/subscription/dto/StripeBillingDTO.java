/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StripeBillingDTO {
  @JsonProperty("line1") private String line1;
  @JsonProperty("line2") private String line2;
  @JsonProperty("city") private String city;
  @JsonProperty("state") private String state;
  @JsonProperty("zipCode") private String zipCode;
  @JsonProperty("country") private String country;
  @JsonProperty("creditCardId") private String creditCardId;

  public StripeBillingDTO() {}

  public StripeBillingDTO(
      String line1, String line2, String city, String state, String zipCode, String country, String creditCardId) {
    this.line1 = line1;
    this.line2 = line2;
    this.city = city;
    this.state = state;
    this.zipCode = zipCode;
    this.country = country;
    this.creditCardId = creditCardId;
  }
}
