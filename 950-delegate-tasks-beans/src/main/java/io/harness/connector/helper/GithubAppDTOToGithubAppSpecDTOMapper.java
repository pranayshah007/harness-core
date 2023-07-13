/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class GithubAppDTOToGithubAppSpecDTOMapper {
  public GithubAppSpecDTO toGitHubSpec(GithubAppDTO githubAppDTO) {
    return GithubAppSpecDTO.builder()
        .applicationId(githubAppDTO.getApplicationId())
        .installationId(githubAppDTO.getInstallationId())
        .privateKeyRef(githubAppDTO.getPrivateKeyRef())
        .applicationIdRef(githubAppDTO.getApplicationIdRef())
        .installationIdRef(githubAppDTO.getInstallationIdRef())
        .build();
  }
}
