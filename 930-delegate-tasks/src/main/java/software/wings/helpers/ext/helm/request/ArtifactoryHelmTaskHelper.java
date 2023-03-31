/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.helm.request;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.ArtifactoryConfigRequest;

import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDC)
public class ArtifactoryHelmTaskHelper {
  @Inject EncryptionService encryptionService;
  @Inject ArtifactoryService artifactoryService;

  @NotNull
  public String getArtifactoryRepoNameFromHelmConfig(HttpHelmRepoConfig helmRepoConfig) {
    String repoName = helmRepoConfig.getChartRepoUrl().split("/artifactory/", 2)[1];
    if (repoName.endsWith("/")) {
      repoName = repoName.substring(0, repoName.length() - 1);
    }
    return repoName;
  }

  public ArtifactoryConfigRequest getArtifactoryConfigRequestFromHelmRepoConfig(HttpHelmRepoConfig helmRepoConfig) {
    String baseUrl = helmRepoConfig.getChartRepoUrl().split("/artifactory/", 2)[0] + "/artifactory/";
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl(baseUrl)
        .username(helmRepoConfig.getUsername())
        .password(helmRepoConfig.getPassword())
        .hasCredentials(helmRepoConfig.getUsername() != null || helmRepoConfig.getPassword() != null)
        .build();
  }

  public static boolean shouldFetchHelmChartsFromArtifactory(HelmChartConfigParams helmChartConfigParams) {
    return helmChartConfigParams != null && helmChartConfigParams.isBypassHelmFetch()
        && helmChartConfigParams.getHelmRepoConfig() instanceof HttpHelmRepoConfig
        && ((HttpHelmRepoConfig) helmChartConfigParams.getHelmRepoConfig()).getChartRepoUrl().contains("/artifactory/");
  }
}
