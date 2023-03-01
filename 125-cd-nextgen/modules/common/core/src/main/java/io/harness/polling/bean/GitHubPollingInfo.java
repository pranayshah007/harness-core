/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitpolling.bean.GitPollingConfig;
import io.harness.cdng.gitpolling.bean.yaml.GitHubPollingConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.trigger.WebhookSource;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class GitHubPollingInfo implements GitPollingInfo {
  @JsonTypeInfo(use = NAME, property = "storeType", include = EXTERNAL_PROPERTY, visible = true) StoreConfig store;

  String connectorRef;
  String eventType;
  String webhookId;

  int pollInterval;
  String repository;
  @Override
  public String getType() {
    return WebhookSource.GITHUB.name();
  }

  @Override
  public GitPollingConfig toGitPollingConfig() {
    return GitHubPollingConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .repository(ParameterField.<String>builder().value(repository).build())
        .webhookId(webhookId)
        .pollInterval(pollInterval)
        .build();
  }
}
