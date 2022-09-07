/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SLOHealthListView {
  @NotNull String sloIdentifier;
  @NotNull String name;
  @NotNull String monitoredServiceIdentifier;
  @NotNull String monitoredServiceName;
  @NotNull String healthSourceIdentifier;
  @NotNull String healthSourceName;
  @NotNull String serviceIdentifier;
  @NotNull String environmentIdentifier;
  @NotNull String environmentName;
  @NotNull String serviceName;
  Map<String, String> tags;
  String description;
  @NotNull double burnRate;
  @NotNull double errorBudgetRemainingPercentage;
  @NotNull int errorBudgetRemaining;
  @NotNull int totalErrorBudget;
  @NotNull SLOTargetType sloTargetType;
  @NotNull double sloTargetPercentage;
  @NotNull int noOfActiveAlerts;
  @NotNull int noOfMaximumAlerts;
  @NotNull
  public ErrorBudgetRisk getErrorBudgetRisk() {
    return ErrorBudgetRisk.getFromPercentage(errorBudgetRemainingPercentage);
  }
}
