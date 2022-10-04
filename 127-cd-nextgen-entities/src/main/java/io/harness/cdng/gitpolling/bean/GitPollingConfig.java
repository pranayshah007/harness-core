/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitpolling.bean;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WithIdentifier;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(HarnessTeam.PIPELINE)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
public interface GitPollingConfig extends WithIdentifier, OverridesApplier<GitPollingConfig> {
  @JsonIgnore GitPollingSourceType getSourceType();

  String getWebhookId();

  int getPollInterval();

  void setIdentifier(String identifier);

  @Override @JsonIgnore String getIdentifier();
}
