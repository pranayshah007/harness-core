/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(DX)
public enum RepoProviders {
  @JsonProperty("github") GITHUB,
  @JsonProperty("gitlab") GITLAB,
  @JsonProperty("bitbucket") BITBUCKET,
  @JsonProperty("azure") AZURE,
  @JsonProperty("bitbucketserver") BITBUCKET_SERVER,
  @JsonProperty("unknown") UNKNOWN;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static RepoProviders fromString(@JsonProperty("repoProviderName") String repoProvider) {
    for (RepoProviders repoProviderEnum : RepoProviders.values()) {
      if (repoProviderEnum.name().equalsIgnoreCase(repoProvider)) {
        return repoProviderEnum;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + repoProvider);
  }
}
