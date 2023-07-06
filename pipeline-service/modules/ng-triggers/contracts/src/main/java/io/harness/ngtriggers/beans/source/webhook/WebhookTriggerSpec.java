/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GithubTriggerSpec.class, name = "GITHUB")
  , @JsonSubTypes.Type(value = GitlabTriggerSpec.class, name = "GITLAB"),
      @JsonSubTypes.Type(value = BitbucketTriggerSpec.class, name = "BITBUCKET"),
      @JsonSubTypes.Type(value = AwsCodeCommitTriggerSpec.class, name = "AWS_CODECOMMIT"),
      @JsonSubTypes.Type(value = CustomWebhookTriggerSpec.class, name = "CUSTOM"),
      @JsonSubTypes.Type(value = HarnessScmTriggerSpec.class, name = "HARNESS")
})
@OwnedBy(PIPELINE)
public interface WebhookTriggerSpec {
  RepoSpec getRepoSpec();
  WebhookEvent getEvent();
  List<WebhookAction> getActions();
  List<WebhookCondition> getPayloadConditions();
  String getJexlCondition();
  List<String> getPathFilters();
  WebhookSourceRepo getType();

  default List<WebhookCondition> getHeaderConditions() {
    return null;
  }
}
