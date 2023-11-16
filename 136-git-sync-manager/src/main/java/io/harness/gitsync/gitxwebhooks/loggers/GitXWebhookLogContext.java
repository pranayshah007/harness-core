/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.gitsync.gitxwebhooks.loggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.NgTriggerAutoLogContext.*;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(PIPELINE)
public class GitXWebhookLogContext extends AutoLogContext {
  public static final String REPO_NAME_KEY = "repoName";
  public static final String WEBHOOK_IDENTIFIER_KEY = "webhookIdentifier";
  public static final String CONNECTOR_REF_KEY = "connectorRef";
  public static final String CONTEXT_KEY = "contextKey";

  public GitXWebhookLogContext(CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO) {
    super(setContextMap(createGitXWebhookRequestDTO.getScope(), createGitXWebhookRequestDTO.getWebhookIdentifier(),
              createGitXWebhookRequestDTO.getConnectorRef(), createGitXWebhookRequestDTO.getRepoName()),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  public GitXWebhookLogContext(GetGitXWebhookRequestDTO getGitXWebhookRequestDTO) {
    super(
        setContextMap(getGitXWebhookRequestDTO.getScope(), getGitXWebhookRequestDTO.getWebhookIdentifier(), null, null),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  public GitXWebhookLogContext(DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO) {
    super(setContextMap(
              deleteGitXWebhookRequestDTO.getScope(), deleteGitXWebhookRequestDTO.getWebhookIdentifier(), null, null),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  public GitXWebhookLogContext(ListGitXWebhookRequestDTO listGitXWebhookRequestDTO) {
    super(setContextMap(
              listGitXWebhookRequestDTO.getScope(), listGitXWebhookRequestDTO.getWebhookIdentifier(), null, null),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  public GitXWebhookLogContext(GitXEventsListRequestDTO gitXEventsListRequestDTO) {
    super(
        setContextMap(gitXEventsListRequestDTO.getScope(), gitXEventsListRequestDTO.getWebhookIdentifier(), null, null),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  public GitXWebhookLogContext(UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO,
      UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO) {
    super(setContextMap(updateGitXWebhookCriteriaDTO.getScope(), updateGitXWebhookCriteriaDTO.getWebhookIdentifier(),
              updateGitXWebhookRequestDTO.getConnectorRef(), updateGitXWebhookRequestDTO.getRepoName()),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> setContextMap(
      Scope scope, String webhookIdentifier, String connectorRef, String repoName) {
    Map<String, String> logContextMap = new HashMap<>();
    setContextIfNotNull(logContextMap, ACCOUNT_KEY, scope.getAccountIdentifier());
    setContextIfNotNull(logContextMap, ORG_KEY, scope.getOrgIdentifier());
    setContextIfNotNull(logContextMap, PROJECT_KEY, scope.getProjectIdentifier());
    setContextIfNotNull(logContextMap, WEBHOOK_IDENTIFIER_KEY, webhookIdentifier);
    setContextIfNotNull(logContextMap, REPO_NAME_KEY, repoName);
    setContextIfNotNull(logContextMap, CONNECTOR_REF_KEY, connectorRef);
    setContextIfNotNull(logContextMap, CONTEXT_KEY, String.valueOf(java.util.UUID.randomUUID()));
    return logContextMap;
  }

  private static void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
