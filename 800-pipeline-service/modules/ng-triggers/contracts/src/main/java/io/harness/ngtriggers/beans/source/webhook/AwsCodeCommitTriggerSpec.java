/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.AWS_CODECOMMIT;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
public class AwsCodeCommitTriggerSpec implements WebhookTriggerSpec {
  GitRepoSpec gitRepoSpec;
  WebhookEvent event;
  List<WebhookAction> actions;
  List<WebhookCondition> payloadConditions;
  String jexlCondition;
  List<String> pathFilters;

  @Override
  public WebhookSourceRepo getType() {
    return AWS_CODECOMMIT;
  }

  @Override
  public RepoSpec getRepoSpec() {
    return gitRepoSpec;
  }
}
