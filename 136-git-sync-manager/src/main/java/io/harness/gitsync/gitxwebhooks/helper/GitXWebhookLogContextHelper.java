/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.helper;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.gitsync.common.beans.GitOperation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class GitXWebhookLogContextHelper {
  public static final String REPO_NAME_KEY = "repoName";
  public static final String WEBHOOK_IDENTIFIER_KEY = "webhookIdentifier";
  public static final String WEBHOOK_NAME_KEY = "webhookName";
  public static final String CONNECTOR_REF_KEY = "connectorRef";
  public static final String CONTEXT_KEY = "contextKey";

  public static Map<String, String> setContextMap(
      String accountIdentifier, String webhookIdentifier, String connectorRef, String repoName, String webhookName) {
    Map<String, String> logContextMap = new HashMap<>();
    setContextIfNotNull(logContextMap, ACCOUNT_KEY, accountIdentifier);
    setContextIfNotNull(logContextMap, WEBHOOK_IDENTIFIER_KEY, webhookIdentifier);
    setContextIfNotNull(logContextMap, REPO_NAME_KEY, repoName);
    setContextIfNotNull(logContextMap, WEBHOOK_NAME_KEY, webhookName);
    setContextIfNotNull(logContextMap, CONNECTOR_REF_KEY, connectorRef);
    setContextIfNotNull(logContextMap, CONTEXT_KEY, String.valueOf(java.util.UUID.randomUUID()));
    return logContextMap;
  }

  private void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
