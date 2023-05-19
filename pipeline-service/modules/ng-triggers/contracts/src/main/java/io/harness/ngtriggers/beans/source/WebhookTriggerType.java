/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.AWS_CODECOMMIT_REPO;
import static io.harness.ngtriggers.Constants.AZURE_REPO;
import static io.harness.ngtriggers.Constants.BITBUCKET_REPO;
import static io.harness.ngtriggers.Constants.CUSTOM_REPO;
import static io.harness.ngtriggers.Constants.GITHUB_REPO;
import static io.harness.ngtriggers.Constants.GITLAB_REPO;
import static io.harness.ngtriggers.Constants.HARNESS_REPO;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("ngTriggerType")
@OwnedBy(PIPELINE)
public enum WebhookTriggerType {
  @JsonProperty(AZURE_REPO) AZURE(AZURE_REPO, "AZURE_REPO"),
  @JsonProperty(GITHUB_REPO) GITHUB(GITHUB_REPO, "GITHUB"),
  @JsonProperty(GITLAB_REPO) GITLAB(GITLAB_REPO, "GITLAB"),
  @JsonProperty(BITBUCKET_REPO) BITBUCKET(BITBUCKET_REPO, "BITBUCKET"),
  @JsonProperty(CUSTOM_REPO) CUSTOM(CUSTOM_REPO, "CUSTOM"),
  @JsonProperty(AWS_CODECOMMIT_REPO) AWS_CODECOMMIT(AWS_CODECOMMIT_REPO, "AWS_CODECOMMIT"),
  @JsonProperty(HARNESS_REPO) HARNESS(HARNESS_REPO, "HARNESS");

  private String value;
  private String entityMetadataName;

  WebhookTriggerType(String value, String entityMetadataName) {
    this.value = value;
    this.entityMetadataName = entityMetadataName;
  }

  public String getValue() {
    return value;
  }

  public String getEntityMetadataName() {
    return entityMetadataName;
  }
}
