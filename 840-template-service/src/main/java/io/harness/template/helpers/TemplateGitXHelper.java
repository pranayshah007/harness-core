/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeBuilder;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.SCMGitSyncHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class TemplateGitXHelper {
  @Inject SCMGitSyncHelper scmGitSyncHelper;

  public String getWorkingBranch(String entityRepoURL) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = buildScope(gitEntityInfo);
    String branchName = gitEntityInfo.getBranch();
    if (isParentReferenceEntityNotPresent(gitEntityInfo)) {
      return branchName;
    }
    String parentEntityRepoUrl = getRepoUrl(scope);
    if (gitEntityInfo.isNewBranch()) {
      branchName = gitEntityInfo.getBaseBranch();
    }
    if (null != parentEntityRepoUrl && !parentEntityRepoUrl.equals(entityRepoURL)) {
      branchName = "";
    }
    return branchName;
  }

  private String getRepoUrl(Scope scope) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoUrl())) {
      return gitEntityInfo.getParentEntityRepoUrl();
    }
    String parentEntityRepoUrl = scmGitSyncHelper
                                     .getRepoUrl(scope, gitEntityInfo.getParentEntityRepoName(),
                                         gitEntityInfo.getParentEntityConnectorRef(), Collections.emptyMap())
                                     .getRepoUrl();

    gitEntityInfo.setParentEntityRepoUrl(parentEntityRepoUrl);
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    return parentEntityRepoUrl;
  }

  private Scope buildScope(GitEntityInfo gitEntityInfo) {
    ScopeBuilder scope = Scope.builder();
    if (gitEntityInfo != null) {
      if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityAccountIdentifier())) {
        scope.accountIdentifier(gitEntityInfo.getParentEntityAccountIdentifier());
      }
      if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityOrgIdentifier())) {
        scope.orgIdentifier(gitEntityInfo.getParentEntityOrgIdentifier());
      }
      if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityProjectIdentifier())) {
        scope.projectIdentifier(gitEntityInfo.getParentEntityProjectIdentifier());
      }
    }
    return scope.build();
  }

  private boolean isParentReferenceEntityNotPresent(GitEntityInfo gitEntityInfo) {
    return GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoName())
        && GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityConnectorRef());
  }
}
