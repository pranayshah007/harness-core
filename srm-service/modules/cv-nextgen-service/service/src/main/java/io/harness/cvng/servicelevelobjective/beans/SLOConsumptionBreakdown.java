/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.core.beans.params.ProjectParams;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class SLOConsumptionBreakdown {
  @NotNull String sloIdentifier;
  @NotNull String sloName;
  String monitoredServiceIdentifier;
  String serviceName;
  String environmentIdentifier;
  ServiceLevelIndicatorType sliType;
  @NotNull double weightagePercentage;
  @NotNull double sloTargetPercentage;
  @NotNull double sliStatusPercentage;
  @NotNull long errorBudgetBurned;
  Integer contributedErrorBudgetBurned;
  @NotNull ProjectParams projectParams;
  String orgName;
  String projectName;

  SLOError sloError;
}
