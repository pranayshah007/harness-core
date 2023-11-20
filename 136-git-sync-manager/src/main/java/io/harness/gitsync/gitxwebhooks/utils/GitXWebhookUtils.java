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
import static io.harness.data.structure.HarnessStringUtils.isStringValueMatching;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class GitXWebhookUtils {
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

  public boolean isBiDirectionalSyncEnabled(Scope fileScope, List<GitXWebhook> gitXWebhookList, String filepath) {
    List<GitXWebhook> enabledWebhooks = getEnabledWebhooks(gitXWebhookList);
    List<GitXWebhook> webhooksWithMatchingFolderPaths = getWebhooksWithMatchingFolderPaths(enabledWebhooks, filepath);
    if (isEmpty(webhooksWithMatchingFolderPaths)) {
      return false;
    }
    GitXWebhook gitXWebhook = getClosetGitXWebhookForGivenScope(fileScope, webhooksWithMatchingFolderPaths);
    return gitXWebhook != null;
  }

  private List<GitXWebhook> getEnabledWebhooks(List<GitXWebhook> gitXWebhookList) {
    List<GitXWebhook> enabledWebhooks = new ArrayList<>();
    gitXWebhookList.forEach(gitXWebhook -> {
      if (gitXWebhook.getIsEnabled()) {
        enabledWebhooks.add(gitXWebhook);
      }
    });
    return enabledWebhooks;
  }

  private List<GitXWebhook> getWebhooksWithMatchingFolderPaths(List<GitXWebhook> gitXWebhookList, String filepath) {
    List<GitXWebhook> webhooksWithMatchingFolderPaths = new ArrayList<>();
    if (isNotEmpty(gitXWebhookList)) {
      gitXWebhookList.forEach(gitXWebhook -> {
        if (isEmpty(gitXWebhook.getFolderPaths())
            || isNotEmpty(compareFolderPaths(gitXWebhook.getFolderPaths(), Collections.singletonList(filepath)))) {
          webhooksWithMatchingFolderPaths.add(gitXWebhook);
        }
      });
    }
    return webhooksWithMatchingFolderPaths;
  }

  @VisibleForTesting
  GitXWebhook getClosetGitXWebhookForGivenScope(Scope fileScope, List<GitXWebhook> gitXWebhookList) {
    for (GitXWebhook gitXWebhook : gitXWebhookList) {
      Scope webhookScope = Scope.of(
          gitXWebhook.getAccountIdentifier(), gitXWebhook.getOrgIdentifier(), gitXWebhook.getProjectIdentifier());
      if (isMatchingScope(webhookScope, fileScope)) {
        return gitXWebhook;
      }
    }
    return null;
  }

  private boolean isMatchingScope(Scope webhookScope, Scope fileScope) {
    if (isNotEmpty(webhookScope.getProjectIdentifier())) {
      return isProjectScopeMatching(webhookScope, fileScope);
    } else if (isNotEmpty(webhookScope.getOrgIdentifier())) {
      return isOrgScopeMatching(webhookScope, fileScope);
    } else {
      return isAccountScopeMatching(webhookScope, fileScope);
    }
  }

  private boolean isProjectScopeMatching(Scope webhookScope, Scope fileScope) {
    return isStringValueMatching(webhookScope.getProjectIdentifier(), fileScope.getProjectIdentifier())
        && isOrgScopeMatching(webhookScope, fileScope);
  }

  private boolean isOrgScopeMatching(Scope webhookScope, Scope fileScope) {
    return isStringValueMatching(webhookScope.getOrgIdentifier(), fileScope.getOrgIdentifier())
        && isAccountScopeMatching(webhookScope, fileScope);
  }

  private boolean isAccountScopeMatching(Scope webhookScope, Scope fileScope) {
    return webhookScope.getAccountIdentifier().equals(fileScope.getAccountIdentifier());
  }
}