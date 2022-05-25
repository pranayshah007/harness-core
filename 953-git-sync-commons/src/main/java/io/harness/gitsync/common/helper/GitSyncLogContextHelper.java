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
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GitSyncLogContextHelper {
  public static final String GIT_OPERATION_TYPE = "GitOperationType";
  public static final String REPO_NAME_KEY = "repoName";
  public static final String FILEPATH_KEY = "filePath";
  public static final String BRANCH_KEY = "branch";
  private Map<String, String> logContextMap = new HashMap<>();

  public static Map<String, String> setContextMap(Scope scope, String repoName, String branchName, String filePath,
      GitOperation operationType, Map<String, String> contextMap) {
    if (contextMap != null) {
      logContextMap = contextMap;
    }
    setContextIfNotNull(ACCOUNT_KEY, scope.getAccountIdentifier());
    setContextIfNotNull(ORG_KEY, scope.getOrgIdentifier());
    setContextIfNotNull(PROJECT_KEY, scope.getProjectIdentifier());
    setContextIfNotNull(REPO_NAME_KEY, repoName);
    setContextIfNotNull(BRANCH_KEY, branchName);
    setContextIfNotNull(FILEPATH_KEY, filePath);
    setContextIfNotNull(GIT_OPERATION_TYPE, operationType.name());
    return logContextMap;
  }

  private void setContextIfNotNull(String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
