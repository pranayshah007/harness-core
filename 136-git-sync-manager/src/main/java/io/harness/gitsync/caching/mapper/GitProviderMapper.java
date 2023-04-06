/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.entity.GitProvider;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitProviderMapper {
  public GitProvider toEntity(io.harness.gitsync.caching.beans.GitProvider gitProvider) {
    switch (gitProvider) {
      case GITHUB_SAAS:
        return GitProvider.GITHUB_SAAS;
      case BITBUCKET_SAAS:
        return GitProvider.BITBUCKET_SAAS;
      case BITBUCKET_ON_PREM:
        return GitProvider.BITBUCKET_ON_PREM;
      case AZURE_SAAS:
        return GitProvider.AZURE_SAAS;
      case GITLAB_SAAS:
        return GitProvider.GITLAB_SAAS;
      default:
        log.error("No matching git provider found for input : {}", gitProvider);
        return GitProvider.UNKNOWN;
    }
  }
}
