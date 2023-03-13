/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SCM_BAD_REQUEST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ScmException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.USER_FLOW;
import io.harness.manage.GlobalContextManager;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineGitXHelper {
  public void setupGitParentEntityDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repo) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (null != gitEntityInfo) {
      if (!GitAwareContextHelper.isNullOrDefault(orgIdentifier)) {
        gitEntityInfo.setParentEntityOrgIdentifier(orgIdentifier);
      }
      if (!GitAwareContextHelper.isNullOrDefault(projectIdentifier)) {
        gitEntityInfo.setParentEntityProjectIdentifier(projectIdentifier);
      }
      // setting connector and repo name. This is required to pass to child pipeline from parent pipeline
      if (!GitAwareContextHelper.isNullOrDefault(connectorRef)) {
        gitEntityInfo.setParentEntityConnectorRef(connectorRef);
      }
      if (!GitAwareContextHelper.isNullOrDefault(repo)) {
        gitEntityInfo.setParentEntityRepoName(repo);
      }
      gitEntityInfo.setParentEntityAccountIdentifier(accountIdentifier);
      GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    }
  }

  public void setupEntityDetails(EntityGitDetails entityGitDetails) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (null != gitEntityInfo && null != entityGitDetails) {
      if (EmptyPredicate.isNotEmpty(entityGitDetails.getRepoName())) {
        gitEntityInfo.setRepoName(entityGitDetails.getRepoName());
      }
      if (EmptyPredicate.isNotEmpty(entityGitDetails.getBranch())) {
        gitEntityInfo.setBranch(entityGitDetails.getBranch());
      }
      if (EmptyPredicate.isNotEmpty(entityGitDetails.getFilePath())) {
        gitEntityInfo.setFilePath(entityGitDetails.getFilePath());
      }
      if (isNotEmpty(entityGitDetails.getParentEntityConnectorRef())) {
        gitEntityInfo.setParentEntityConnectorRef(entityGitDetails.getParentEntityConnectorRef());
      }
      if (isNotEmpty(entityGitDetails.getParentEntityRepoName())) {
        gitEntityInfo.setParentEntityRepoName(entityGitDetails.getParentEntityRepoName());
      }
    }
  }

  public boolean shouldRetryWithFallBackBranch(
      ScmException scmException, String branchTried, String entityFallBackBranch) {
    return scmException != null && SCM_BAD_REQUEST.equals(scmException.getCode())
        && (isNotEmpty(entityFallBackBranch) && !entityFallBackBranch.equals(branchTried));
  }

  public boolean isExecutionFlow() {
    USER_FLOW user_flow = ThreadOperationContextHelper.getThreadOperationContextUserFlow();
    if (user_flow != null) {
      return user_flow.equals(USER_FLOW.EXECUTION);
    }
    return false;
  }

  public void setUserFlowContext(USER_FLOW userFlow) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(
        ThreadOperationContextHelper.getThreadOperationContext().withUserFlow(userFlow));
  }
}
