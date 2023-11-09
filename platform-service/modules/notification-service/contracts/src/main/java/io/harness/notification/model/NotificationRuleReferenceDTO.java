/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.notification.model;

import io.harness.notification.entities.eventmetadata.NotificationEventParameters;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationRuleReferenceDTO {
  String identifier;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  NotificationEventParameters notificationEventParameters;
}
