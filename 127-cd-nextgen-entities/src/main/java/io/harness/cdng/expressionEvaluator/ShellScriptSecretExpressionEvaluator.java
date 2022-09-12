/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.expressionEvaluator;

import io.harness.expression.EngineExpressionEvaluator;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import software.wings.expression.EncryptedDataDetails;
import software.wings.expression.NgSecretManagerFunctor;
import software.wings.expression.SecretManagerMode;
import software.wings.service.intfc.security.SecretManager;

import javax.cache.Cache;

public class ShellScriptSecretExpressionEvaluator extends EngineExpressionEvaluator {
  private String accountId;
  private String orgId;
  private String projectId;
  private SecretManager secretManager;
  private Cache<String, EncryptedDataDetails> secretsCache;
  private SecretManagerClientService ngSecretService;
  private SecretManagerMode mode;

  public ShellScriptSecretExpressionEvaluator(String accountId, String projectId, String orgId,
      SecretManager secretManager, Cache<String, EncryptedDataDetails> secretsCache,
      SecretManagerClientService ngSecretService, SecretManagerMode mode) {
    super(null);
    this.accountId = accountId;
    this.projectId = projectId;
    this.orgId = orgId;
    this.secretManager = secretManager;
    this.secretsCache = secretsCache;
    this.ngSecretService = ngSecretService;
    this.mode = mode;
  }

  @Override
  protected void initialize() {
    super.initialize();
    this.addToContext("secrets",
        NgSecretManagerFunctor.builder()
            .accountId(accountId)
            .projectId(projectId)
            .orgId(orgId)
            .secretManager(secretManager)
            .secretsCache(secretsCache)
            .ngSecretService(ngSecretService)
            .mode(mode)
            .build());
  }
}
