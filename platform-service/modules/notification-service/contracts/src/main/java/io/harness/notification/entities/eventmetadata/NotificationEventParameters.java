/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities.eventmetadata;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({
  @JsonSubTypes.Type(value = DelegateNotificationEventParameters.class, name = "delegateNotification")
  , @JsonSubTypes.Type(value = ConnectorNotificationEventParameters.class, name = "connectorNotification"),
})
@OwnedBy(PL)
public interface NotificationEventParameters {}
