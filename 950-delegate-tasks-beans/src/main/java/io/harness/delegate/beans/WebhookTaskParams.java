/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class WebhookTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  @Expression(ALLOW_SECRETS) List<String> webhookUrls;
  String message;
  String notificationId;
  private static final Pattern SECRET_EXPRESSION =
      Pattern.compile("\\$\\{ngSecretManager\\.obtain\\(\\\"\\w*[\\.]?\\w*\\\"\\, ([+-]?\\d*|0)\\)\\}");

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (!webhookUrls.isEmpty()) {
      if (!SECRET_EXPRESSION.matcher(webhookUrls.get(0)).matches()) {
        URI uri = null;
        try {
          uri = new URI(webhookUrls.get(0));
          executionCapabilities.add(HttpConnectionExecutionCapability.builder()
                                        .host(uri.getHost())
                                        .scheme(uri.getScheme())
                                        .port(uri.getPort())
                                        .build());
        } catch (URISyntaxException e) {
          log.error("Can't parse webhookurl as URI {}: {}", notificationId, e);
        }
      }
    }
    return executionCapabilities;
  }
}
