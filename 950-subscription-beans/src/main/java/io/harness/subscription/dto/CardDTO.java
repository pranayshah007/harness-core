/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.jvnet.hk2.annotations.Optional;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardDTO {
  private String id;
  private String name;
  private String last4;
  private String funding;
  private Long expireMonth;
  private Long expireYear;
  private String cvcCheck;
  private String brand;
  private String addressCity;
  private String addressCountry;
  private String addressState;
  private String addressZip;
  private String addressLine1;
  private String addressLine2;
  @Optional private Boolean isDefaultCard;
}
