/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.trigger;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ngtriggers.WebhookSecretData;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class TriggerAuthenticationTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  String eventPayload;
  GitRepoType gitRepoType;
  String hashedPayload;
  List<WebhookSecretData> webhookSecretData;

  public TriggerAuthenticationTaskParams(
      String eventPayload, GitRepoType gitRepoType, String hashedPayload, List<WebhookSecretData> webhookSecretData) {
    this.eventPayload = eventPayload;
    this.gitRepoType = gitRepoType;
    this.hashedPayload = hashedPayload;
    this.webhookSecretData = webhookSecretData;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.emptyList();
  }
}
