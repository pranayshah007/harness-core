/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.gitsync.gitxwebhooks.loggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.NgTriggerAutoLogContext.ACCOUNT_KEY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.dtos.GitXCacheUpdateRunnableRequestDTO;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(PIPELINE)
public class GitXWebhookCacheUpdateLogContext extends AutoLogContext {
  public static final String REPO_NAME_KEY = "repoName";
  public static final String EVENT_IDENTIFIER_KEY = "eventIdentifier";
  public static final String BRANCH_KEY = "branch";
  public static final String PROCESSING_FILE_PATHS_KEY = "processingFilePaths";
  public static final String CONTEXT_KEY = "contextKey";

  public GitXWebhookCacheUpdateLogContext(GitXCacheUpdateRunnableRequestDTO gitXCacheUpdateRunnableRequestDTO) {
    super(setContextMap(gitXCacheUpdateRunnableRequestDTO.getAccountIdentifier(),
              gitXCacheUpdateRunnableRequestDTO.getRepoName(), gitXCacheUpdateRunnableRequestDTO.getEventIdentifier(),
              gitXCacheUpdateRunnableRequestDTO.getBranch(), gitXCacheUpdateRunnableRequestDTO.getModifiedFilePaths()),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> setContextMap(String accountIdentifier, String repoName, String eventIdentifier,
      String branch, List<String> modifiedFilePaths) {
    Map<String, String> logContextMap = new HashMap<>();
    setContextIfNotNull(logContextMap, ACCOUNT_KEY, accountIdentifier);
    setContextIfNotNull(logContextMap, REPO_NAME_KEY, repoName);
    setContextIfNotNull(logContextMap, EVENT_IDENTIFIER_KEY, eventIdentifier);
    setContextIfNotNull(logContextMap, BRANCH_KEY, branch);
    setContextIfNotNull(logContextMap, PROCESSING_FILE_PATHS_KEY, modifiedFilePaths);
    setContextIfNotNull(logContextMap, CONTEXT_KEY, String.valueOf(java.util.UUID.randomUUID()));
    return logContextMap;
  }

  private static void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }

  private static void setContextIfNotNull(Map<String, String> logContextMap, String key, List<String> value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, String.join(", ", value));
    }
  }
}
