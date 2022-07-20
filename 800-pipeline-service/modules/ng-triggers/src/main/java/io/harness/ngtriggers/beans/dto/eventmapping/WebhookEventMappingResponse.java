/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto.eventmapping;

import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebhookEventMappingResponse {
  TriggerEventResponse webhookEventResponse;
  ParseWebhookResponse parseWebhookResponse;
  @Default boolean failedToFindTrigger = true;
  boolean isCustomTrigger;
  @Singular List<TriggerDetails> triggers;
}
