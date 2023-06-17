/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup.dto;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.BillingFrequency;
import io.harness.licensing.Edition;
import io.harness.ng.core.user.SignupAction;
import io.harness.ng.core.user.UtmInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@OwnedBy(GTM)
public class SignupDTO {
  String email;
  String password;
  String companyName;
  String name;
  UtmInfo utmInfo;
  String intent;
  SignupAction signupAction;
  Edition edition;
  BillingFrequency billingFrequency;
}
