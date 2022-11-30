package io.harness.cvng.servicelevelobjective.beans;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SLOConsumptionBreakdown {
  @NotNull String sloIdentifier;
  @NotNull String sloName;
  @NotNull String monitoredServiceIdentifier;
  @NotNull String serviceName;
  @NotNull String environmentIdentifier;
  @NotNull ServiceLevelIndicatorType sliType;
  @NotNull double weightagePercentage;
  @NotNull double sloTargetPercentage;
  @NotNull double sliStatusPercentage;
  @NotNull int errorBudgetBurned;
  @NotNull int contributedErrorBudgetBurned;
}
