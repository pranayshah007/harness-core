/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.PUSH_EVENT_TYPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitEvent;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(PIPELINE)
public enum AwsCodeCommitTriggerEvent implements GitEvent {
  @JsonProperty(PUSH_EVENT_TYPE) PUSH(PUSH_EVENT_TYPE);

  private String value;

  AwsCodeCommitTriggerEvent(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
