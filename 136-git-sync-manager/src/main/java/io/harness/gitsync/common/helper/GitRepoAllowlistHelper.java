/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.gitx.GitXSettingsHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class GitRepoAllowlistHelper {
  @Inject private GitXSettingsHelper gitXSettingsHelper;

  public Set<String> filterRepoList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Set<String> repoList) {
    if (repoList.isEmpty()) {
      return Collections.EMPTY_SET;
    }

    List<String> repoAllowlist = getGitRepoAllowlist(accountIdentifier, orgIdentifier, projectIdentifier);
    // Empty RepoAllowList means that setting is not being used.
    if (!repoAllowlist.isEmpty()) {
      Set<String> filterRepoList = new HashSet<>();
      for (String repo : repoList) {
        if (validateIfCurrentRepoIsAllowed(repoAllowlist, repo)) {
          filterRepoList.add(repo);
        }
      }

      return filterRepoList;
    }
    return repoList;
  }

  public void validateRepo(String accountIdentifier, String orgIdentifier, String projectIdentifier, String repoName) {
    List<String> repoAllowlist = getGitRepoAllowlist(accountIdentifier, orgIdentifier, projectIdentifier);

    if (!validateIfCurrentRepoIsAllowed(repoAllowlist, repoName)) {
      String currentScope = getCurrentScopeForErrorMessage(accountIdentifier, orgIdentifier, projectIdentifier);
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format("Please check if repo [%s] is in allowed repository list under default setting for current "
                  + currentScope,
              repoName),
          String.format(
              "GIT operations on repo [%s] are restricted due to default setting for current scope: " + currentScope,
              repoName),
          new InvalidRequestException(
              String.format("Failed to perform GIT operation as access to repo [%s] is forbidden", repoName)));
    }
  }

  private String getCurrentScopeForErrorMessage(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (orgIdentifier == null) {
      return String.format("ACCOUNT scope with account Identifier [%s].", accountIdentifier);
    } else if (projectIdentifier == null) {
      return String.format(
          "ORG scope with account Identifier [%s] and org identifier [%s].", accountIdentifier, orgIdentifier);
    } else {
      return String.format(
          "PROJECT scope with account Identifier [%s], org identifier [%s] and project identifier [%s]",
          accountIdentifier, orgIdentifier, projectIdentifier);
    }
  }

  private boolean validateIfCurrentRepoIsAllowed(List<String> repoAllowlist, String repoName) {
    // Empty RepoAllowList means that setting is not being used.
    if (repoAllowlist.isEmpty()) {
      return true;
    }

    for (String repoAllowlistElem : repoAllowlist) {
      String repoToCompare = repoName;
      if (isAbsoluteRepo(repoAllowlistElem)) {
        repoToCompare = getAbsoluteRepoName(repoName);
      }
      if (repoToCompare.equals(repoAllowlistElem)) {
        return true;
      }
    }
    return false;
  }

  private String getAbsoluteRepoName(String repoAllowlistElem) {
    return repoAllowlistElem.substring(repoAllowlistElem.lastIndexOf('/') + 1);
  }

  // Git providers have directory structures and user may give repoName as "org/repo".
  // This method indicates if the input string has only repo or not.
  private boolean isAbsoluteRepo(String repo) {
    return !repo.contains("/");
  }

  private List<String> getGitRepoAllowlist(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return gitXSettingsHelper.getGitRepoAllowlist(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
