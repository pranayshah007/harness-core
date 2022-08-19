package io.harness.template.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
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

  public String getWorkingBranch(Scope scope, String entityRepoURL) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String branchName = gitEntityInfo.getBranch();
    String parentEntityRepoUrl = getRepoUrl(scope);
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
    String parentEntityRepoUrl;
    if (isInlineEntity(gitEntityInfo)) {
      parentEntityRepoUrl = "";
    } else {
      parentEntityRepoUrl = scmGitSyncHelper
                                .getRepoUrl(scope, gitEntityInfo.getParentEntityRepoName(),
                                    gitEntityInfo.getParentEntityConnectorRef(), Collections.emptyMap())
                                .getRepoUrl();

      gitEntityInfo.setParentEntityRepoUrl(parentEntityRepoUrl);
      GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    }
    return parentEntityRepoUrl;
  }

  private boolean isInlineEntity(GitEntityInfo gitEntityInfo) {
    return GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityRepoName())
        && GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getParentEntityConnectorRef());
  }
}
