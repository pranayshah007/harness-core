/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class MicrosoftTeamsTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  List<String> microsoftTeamsWebhookUrls;
  String message;
  String notificationId;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> compatibility = new ArrayList<>();
    if (!microsoftTeamsWebhookUrls.isEmpty()) {
      URI uri = null;
      try {
        uri = new URI(microsoftTeamsWebhookUrls.get(0));
        compatibility.add(HttpConnectionExecutionCapability.builder()
                              .host(uri.getHost())
                              .scheme(uri.getScheme())
                              .port(uri.getPort())
                              .build());
      } catch (URISyntaxException e) {
        log.error("Can't parse webhookurl as URI {}", notificationId, e);
      }
    }
    return compatibility;
  }
}
