/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLSecretManagerQueryParameters;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class SecretManagerDataFetcher
    extends AbstractObjectDataFetcher<QLSecretManager, QLSecretManagerQueryParameters> {
  @Inject private HPersistence persistence;
  @Inject private SecretManagerController secretManagerController;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  private static final String SECURITY_MANAGER_DOES_NOT_EXIST_MSG = "Secret Manager does not exist";

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLSecretManager fetch(QLSecretManagerQueryParameters qlQuery, String accountId) {
    SecretManagerConfig secretManager = null;

    if (isNotBlank(qlQuery.getSecretManagerId())) {
      secretManager = getById(qlQuery.getSecretManagerId().trim(), accountId);
    }

    if (isNotBlank(qlQuery.getName())) {
      secretManager = getByName(qlQuery.getName().trim(), accountId);
    }

    if (secretManager == null) {
      throw new InvalidRequestException(SECURITY_MANAGER_DOES_NOT_EXIST_MSG, WingsException.USER);
    }

    return secretManagerController.convertToQLSecretManager(secretManager);
  }

  private SecretManagerConfig getByName(String name, String accountId) {
    return secretManagerConfigService.getSecretManagerByName(accountId, name);
  }

  private SecretManagerConfig getById(String id, String accountId) {
    return secretManagerConfigService.getSecretManager(accountId, id, true);
  }
}
