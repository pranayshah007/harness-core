/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ErrorBudgetRemainingPercentageConditionSpec.class, name = "ErrorBudgetRemainingPercentage")
  , @JsonSubTypes.Type(value = ErrorBudgetRemainingMinutesConditionSpec.class, name = "ErrorBudgetRemainingMinutes"),
      @JsonSubTypes.Type(value = ErrorBudgetBurnRateConditionSpec.class, name = "ErrorBudgetBurnRate"),
      @JsonSubTypes.Type(value = ChangeImpactConditionSpec.class, name = "ChangeImpact"),
      @JsonSubTypes.Type(value = HealthScoreConditionSpec.class, name = "HealthScore"),
      @JsonSubTypes.Type(value = ChangeObservedConditionSpec.class, name = "ChangeObserved"),
      @JsonSubTypes.Type(value = ErrorTrackingConditionSpec.class, name = "CodeErrors"),
      @JsonSubTypes.Type(value = DeploymentImpactReportConditionSpec.class, name = "DeploymentImpactReport")
})
public abstract class NotificationRuleConditionSpec {
  @JsonIgnore public abstract NotificationRuleConditionType getType();
}
