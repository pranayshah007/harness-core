/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.core.trigger;

import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.ci.beans.entities.BuildNumberDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;

@Singleton
public class WebhookTriggerProcessor implements TriggerProcessor {
  @Inject WebhookTriggerProcessorUtils webhookTriggerProcessorUtils;

  @Override
  public void validateTriggerCondition() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void validateTriggerAction() {
    throw new UnsupportedOperationException();
  }

  public CIExecutionArgs generateExecutionArgs(
      String pipelineId, String eventPayload, HttpHeaders httpHeaders, BuildNumberDetails buildNumberDetails) {
    // TODO Add branch if require

    WebhookExecutionSource webhookExecutionSource =
        webhookTriggerProcessorUtils.fetchWebhookExecutionSource(eventPayload, httpHeaders);
    return CIExecutionArgs.builder().executionSource(webhookExecutionSource).build();
  }
}
