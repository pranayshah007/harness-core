/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
// This comes in the request
public class GovernancePolicySet {
    @NotNull @NotEmpty String name;
    String description;
    List<String> tags;
    @NotNull @NotEmpty List<String> policySetPolicies;
    @NotNull @NotEmpty List<String> policySetExecutionCron;
    @NotNull @NotEmpty List<String> policySetTargetAccounts;
    List<String> policySetTargetRegions;
    @NotNull @NotEmpty String cloudProvider;
    Boolean isEnabled;
}
