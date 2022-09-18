package io.harness.template.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeBuilder;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.template.utils.TemplateUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Singleton
public class TemplateGitXServiceImpl implements TemplateGitXService {
  SCMGitSyncHelper scmGitSyncHelper;
  NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;
  GitSyncSdkService gitSyncSdkService;

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

  public boolean isNewGitXEnabled(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    if (templateToSave.getProjectIdentifier() != null) {
      return isGitSimplificationEnabledForAProject(templateToSave, gitEntityInfo);
    } else {
      return ngTemplateFeatureFlagHelperService.isEnabled(
                 templateToSave.getAccountId(), FeatureName.NG_TEMPLATE_GITX_ACCOUNT_ORG)
          && TemplateUtils.isRemoteEntity(gitEntityInfo);
    }
  }

  private boolean isGitSimplificationEnabledForAProject(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    return gitSyncSdkService.isGitSimplificationEnabled(templateToSave.getAccountIdentifier(),
               templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier())
        && TemplateUtils.isRemoteEntity(gitEntityInfo);
  }
}
