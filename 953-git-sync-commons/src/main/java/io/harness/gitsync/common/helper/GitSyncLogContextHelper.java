/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.gitsync.common.beans.GitOperation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GitSyncLogContextHelper {
  public static final String GIT_OPERATION_TYPE = "gitOperationType";
  public static final String REPO_NAME_KEY = "repoName";
  public static final String FILEPATH_KEY = "filePath";
  public static final String BRANCH_KEY = "branch";
  public static final String COMMIT_KEY = "commitId";
  public static final String CONTEXT_KEY = "contextKey";

  public static Map<String, String> setContextMap(Scope scope, String repoName, String branchName, String commitId,
      String filePath, GitOperation operationType, Map<String, String> contextMap) {
    Map<String, String> logContextMap = new HashMap<>();
    if (isNotEmpty(contextMap)) {
      logContextMap.putAll(contextMap);
    }
    setContextIfNotNull(logContextMap, ACCOUNT_KEY, scope.getAccountIdentifier());
    setContextIfNotNull(logContextMap, ORG_KEY, scope.getOrgIdentifier());
    setContextIfNotNull(logContextMap, PROJECT_KEY, scope.getProjectIdentifier());
    setContextIfNotNull(logContextMap, REPO_NAME_KEY, repoName);
    setContextIfNotNull(logContextMap, BRANCH_KEY, branchName);
    setContextIfNotNull(logContextMap, COMMIT_KEY, commitId);
    setContextIfNotNull(logContextMap, FILEPATH_KEY, filePath);
    setContextIfNotNull(logContextMap, GIT_OPERATION_TYPE, operationType.name());
    setContextIfNotNull(logContextMap, CONTEXT_KEY, String.valueOf(UUID.randomUUID()));
    return logContextMap;
  }

  private void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
