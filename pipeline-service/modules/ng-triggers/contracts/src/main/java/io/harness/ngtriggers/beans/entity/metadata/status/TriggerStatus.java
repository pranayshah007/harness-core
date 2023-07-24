/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity.metadata.status;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TriggerStatusKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PIPELINE)
public class TriggerStatus {
  PollingSubscriptionStatus pollingSubscriptionStatus;
  ValidationStatus validationStatus;
  WebhookAutoRegistrationStatus webhookAutoRegistrationStatus;
  WebhookInfo webhookInfo;
  StatusResult status;
  List<String> detailMessages;
  Long lastPollingUpdate;
  List<String> lastPolled; // A list of the last successfully polled tags/versions/ids.
}
