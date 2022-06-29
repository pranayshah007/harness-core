/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import static io.harness.beans.execution.ExecutionSource.Type.WEBHOOK;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName("WEBHOOK")
@TypeAlias("webhookExecutionSource")
@RecasterAlias("io.harness.beans.execution.WebhookExecutionSource")
public class WebhookExecutionSource implements ExecutionSource {
  private WebhookGitUser user;
  private WebhookEvent webhookEvent;
  private String triggerName;

  @Override
  public Type getType() {
    return WEBHOOK;
  }
}
