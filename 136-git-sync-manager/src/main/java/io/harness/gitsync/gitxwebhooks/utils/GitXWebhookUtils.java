/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookService;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class GitXWebhookUtils {
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject GitXWebhookService gitXWebhookService;
  @Inject NGSettingsClient ngSettingsClient;

  public List<String> compareFolderPaths(List<String> webhookFolderPaths, List<String> modifiedFilePaths) {
    ArrayList<String> matchingFolderPaths = new ArrayList<>();
    if (isEmpty(modifiedFilePaths)) {
      return matchingFolderPaths;
    }
    webhookFolderPaths.forEach(webhookFolderPath -> {
      int webhookFolderPathLength = webhookFolderPath.length();
      modifiedFilePaths.forEach(modifiedFilePath -> {
        int modifiedFilePathLength = modifiedFilePath.length();
        if (webhookFolderPathLength > modifiedFilePathLength) {
          return;
        }
        String modifiedFilePathSubstring = modifiedFilePath.substring(0, webhookFolderPathLength);
        if (webhookFolderPath.equals(modifiedFilePathSubstring)) {
          matchingFolderPaths.add(modifiedFilePath);
        }
      });
    });
    return matchingFolderPaths;
  }

  public boolean isBiDirectionalSyncApplicable(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO) {
    List<String> matchingFolderPaths = new ArrayList<>();
    if (isBiDirectionalSyncEnabledInSettings(scmGetFileByBranchRequestDTO.getScope().getAccountIdentifier())
        && ngFeatureFlagHelperService.isEnabled(
            scmGetFileByBranchRequestDTO.getScope().getAccountIdentifier(), FeatureName.PIE_GIT_BI_DIRECTIONAL_SYNC)) {
      Optional<GitXWebhook> optionalGitXWebhook =
          gitXWebhookService.getGitXWebhook(scmGetFileByBranchRequestDTO.getScope().getAccountIdentifier(), null,
              scmGetFileByBranchRequestDTO.getRepoName());
      if (optionalGitXWebhook.isPresent() && optionalGitXWebhook.get().getIsEnabled()) {
        if (isEmpty(optionalGitXWebhook.get().getFolderPaths())) {
          return true;
        } else {
          matchingFolderPaths = compareFolderPaths(optionalGitXWebhook.get().getFolderPaths(),
              Collections.singletonList(scmGetFileByBranchRequestDTO.getFilePath()));
        }
      }
    }
    return isNotEmpty(matchingFolderPaths);
  }

  public boolean isBiDirectionalSyncEnabledInSettings(String accountId) {
    String isBiDirectionalSyncEnabledString =
        NGRestUtils
            .getResponse(
                ngSettingsClient.getSetting(GitSyncConstants.ENABLE_BI_DIRECTIONAL_SYNC, accountId, null, null))
            .getValue();
    return GitSyncConstants.TRUE_VALUE.equals(isBiDirectionalSyncEnabledString);
  }
}