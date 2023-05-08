/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.beans;

import io.harness.cvng.beans.change.ChangeCategory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeImpactConditionSpec extends NotificationRuleConditionSpec {
  @Deprecated List<MonitoredServiceChangeEventType> changeEventTypes;
  List<ChangeCategory> changeCategories;
  @NonNull @Min(0) @Max(100) Double threshold;
  @NonNull String period;

  @Override
  public NotificationRuleConditionType getType() {
    return NotificationRuleConditionType.CHANGE_IMPACT;
  }
}
