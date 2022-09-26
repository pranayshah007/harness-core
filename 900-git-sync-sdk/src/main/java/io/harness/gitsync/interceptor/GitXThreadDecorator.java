/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.Priorities.HEADER_DECORATOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.GitXNewApiConstants;
import io.harness.manage.GlobalContextManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Priority(HEADER_DECORATOR)
@Slf4j
@OwnedBy(PL)
public class GitXThreadDecorator implements ContainerRequestFilter, ContainerResponseFilter {
  // Used for new APIs with spec-first approach. [only supports params needed for Git Experience and not Git-Sync]
  @Override
  public void filter(ContainerRequestContext requestContext) {
    String path = requestContext.getUriInfo().getPath();
    if (!path.contains("v1")) { // remove this check after Git-Sync APIs are deprecated.
      return;
    }
    MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    final String branchName =
        getRequestParamFromContextWithoutDecoding(GitXNewApiConstants.BRANCH_KEY, queryParameters);
    final String filePath = getRequestParamFromContext(GitXNewApiConstants.FILE_PATH_KEY, queryParameters);
    final String commitMsg = getRequestParamFromContext(GitXNewApiConstants.COMMIT_MSG_KEY, queryParameters);
    final String lastObjectId = getRequestParamFromContext(GitXNewApiConstants.LAST_OBJECT_ID_KEY, queryParameters);
    final String isNewBranch = getRequestParamFromContext(GitXNewApiConstants.NEW_BRANCH_KEY, queryParameters);
    final String baseBranch = getRequestParamFromContext(GitXNewApiConstants.BASE_BRANCH_KEY, queryParameters);
    final String connectorRef = getRequestParamFromContext(GitXNewApiConstants.CONNECTOR_REF_KEY, queryParameters);
    final String storeType = getRequestParamFromContext(GitXNewApiConstants.STORE_TYPE_KEY, queryParameters);
    final String repoName = getRequestParamFromContext(GitXNewApiConstants.REPO_KEY, queryParameters);
    final String lastCommitId = getRequestParamFromContext(GitXNewApiConstants.LAST_COMMIT_ID_KEY, queryParameters);
    final GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                            .branch(branchName)
                                            .filePath(filePath)
                                            .commitMsg(commitMsg)
                                            .lastObjectId(lastObjectId)
                                            .isNewBranch(Boolean.parseBoolean(isNewBranch))
                                            .baseBranch(baseBranch)
                                            .connectorRef(connectorRef)
                                            .storeType(StoreType.getFromStringOrNull(storeType))
                                            .repoName(repoName)
                                            .lastCommitId(lastCommitId)
                                            .build();
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfo).build());
  }

  @VisibleForTesting
  String getRequestParamFromContext(String key, MultivaluedMap<String, String> queryParameters) {
    try {
      // browser converts the query param like 'testing/abc' to 'testing%20abc',
      // we use decode to convert the string back to 'testing/abc'
      String gitExRequestParamValue = getRequestParamFromContextWithoutDecoding(key, queryParameters);
      if (gitExRequestParamValue == null) {
        return null;
      }
      return URLDecoder.decode(gitExRequestParamValue, Charsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      log.error("Error in setting request param for {}", key, e);
    }
    return null;
  }

  @VisibleForTesting
  String getRequestParamFromContextWithoutDecoding(String key, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key);
  }

  @Override
  public void filter(
      ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) {
    GlobalContextManager.unset();
  }
}