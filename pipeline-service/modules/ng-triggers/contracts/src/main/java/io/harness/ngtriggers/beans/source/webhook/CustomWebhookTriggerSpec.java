/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.CUSTOM;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(PIPELINE)
public class CustomWebhookTriggerSpec implements WebhookTriggerSpec {
  List<WebhookCondition> payloadConditions;
  String jexlCondition;
  List<WebhookCondition> headerConditions;

  public void setRepoSpec(RepoSpec repoUrl) {}
  public void setEvent(WebhookEvent webhookEvent) {}

  public void setActions(List<WebhookAction> webhookActions) {}

  public void setPathFilters(List<String> pathFilters) {}

  @Override
  public RepoSpec getRepoSpec() {
    return null;
  }

  @Override
  public WebhookEvent getEvent() {
    return null;
  }

  @Override
  public List<WebhookAction> getActions() {
    return null;
  }

  @Override
  public List<String> getPathFilters() {
    return null;
  }

  @Override
  public WebhookSourceRepo getType() {
    return CUSTOM;
  }
}
