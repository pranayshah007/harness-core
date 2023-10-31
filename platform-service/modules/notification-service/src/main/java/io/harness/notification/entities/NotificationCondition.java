/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Data
@Builder
@EqualsAndHashCode()
@JsonTypeName("NotificationCondition")
@FieldNameConstants(innerTypeName = "NotificationConditionKeys")
public class NotificationCondition {
  String conditionName;
  List<NotificationEventConfig> notificationEventConfigs;
}
