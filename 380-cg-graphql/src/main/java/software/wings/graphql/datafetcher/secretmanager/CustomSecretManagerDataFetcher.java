/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretmanager;

import com.google.inject.Inject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataParams;
import software.wings.graphql.datafetcher.secretManager.SecretManagerController;
import software.wings.graphql.datafetcher.secretManager.SecretManagerMutationDataFetcher;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;

import software.wings.graphql.schema.mutation.secretManager.QLCreateSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLCustomSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLUpdateCustomSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLUpdateSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLEncryptedDataParams;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager;
import software.wings.graphql.schema.type.secretManagers.QLSecretManagerType;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.intfc.security.CustomSecretsManagerService;

import java.util.HashSet;
import java.util.Set;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

@OwnedBy(PL)
public class CustomSecretManagerDataFetcher implements SecretManagerMutationDataFetcher {
  @Inject private CustomSecretsManagerService customSecretsManagerService;
  @Inject private SecretManagerController secretManagerController;
  @Inject private UsageScopeController usageScopeController;

  @Override
  public QLSecretManager createSecretManager(QLCreateSecretManagerInput input, String accountId) {
    QLCustomSecretManagerInput customSecretManagerInput = input.getCustomSecretManagerInput();

    if (customSecretManagerInput == null) {
      throw new InvalidRequestException("Custom Secret Manager config is not provided");
    }

    CustomSecretsManagerConfig customSecretsManagerConfig = createCustomSMConfig(customSecretManagerInput, accountId);
    String uuid = customSecretsManagerService.saveSecretsManager(accountId, customSecretsManagerConfig);

    SecretManagerConfig secretManagerConfig = customSecretsManagerService.getSecretsManager(accountId, uuid);

    return secretManagerController.convertToQLSecretManager(secretManagerConfig);
  }

  @Override
  public QLSecretManager updateSecretManager(QLUpdateSecretManagerInput input, String accountId) {
    String secretManagerId = input.getSecretManagerId();
    QLUpdateCustomSecretManagerInput customSecretManagerInput = input.getCustomSecretManagerInput();

    if (customSecretManagerInput == null) {
      throw new InvalidRequestException("Custom Secret Manager config is not provided");
    }

    CustomSecretsManagerConfig customSecretsManagerConfig;
    try {
      customSecretsManagerConfig = customSecretsManagerService.getSecretsManager(accountId, secretManagerId);
    } catch (ClassCastException ex) {
      throw new InvalidRequestException("Secret manager with given id is not of type " + QLSecretManagerType.CUSTOM);
    }

    if (customSecretsManagerConfig == null) {
      throw new InvalidRequestException("Secret manager with given id doesn't exist");
    }

    updateCustomSecretManagerConfig(customSecretsManagerConfig, customSecretManagerInput, accountId);

    customSecretsManagerService.updateSecretsManager(accountId, customSecretsManagerConfig);

    SecretManagerConfig secretManagerConfig = customSecretsManagerService.getSecretsManager(accountId, secretManagerId);
    return secretManagerController.convertToQLSecretManager(secretManagerConfig);
  }

  @Override
  public void deleteSecretManager(String accountId, String secretManagerId) {
    customSecretsManagerService.deleteSecretsManager(accountId, secretManagerId);
  }

  private CustomSecretsManagerConfig createCustomSMConfig(QLCustomSecretManagerInput input, String accountId) {
    return CustomSecretsManagerConfig.builder()
        .name(input.getName())
        .templateId(input.getTemplateId())
        .delegateSelectors(input.getDelegateSelectors())
        .executeOnDelegate(input.isExecuteOnDelegate())
        .isConnectorTemplatized(input.isConnectorTemplatized())
        .testVariables(obtainTestVariables(input.getTestVariables()))
        .commandPath(input.getCommandPath())
        .host(input.getHost())
        .connectorId(input.getConnectorId())
        .usageRestrictions(usageScopeController.populateUsageRestrictions(input.getUsageScope(), accountId))
        .build();
  }

  private Set<EncryptedDataParams> obtainTestVariables(Set<QLEncryptedDataParams> testVariables) {
    Set<EncryptedDataParams> encryptedDataParams = new HashSet<>();
    if (isNotEmpty(testVariables)) {
      for (QLEncryptedDataParams testVariable : testVariables) {
        encryptedDataParams.add(
            EncryptedDataParams.builder().name(testVariable.getName()).value(testVariable.getValue()).build());
      }
    }
    return encryptedDataParams;
  }

  private void updateCustomSecretManagerConfig(
      CustomSecretsManagerConfig customSecretsManagerConfig, QLUpdateCustomSecretManagerInput input, String accountId) {
    if (isNotEmpty(input.getName())) {
      customSecretsManagerConfig.setName(input.getName());
    }

    if (isNotEmpty(input.getTemplateId())) {
      customSecretsManagerConfig.setTemplateId(input.getTemplateId());
    }

    if (isNotEmpty(input.getTestVariables())) {
      customSecretsManagerConfig.setTestVariables(obtainTestVariables(input.getTestVariables()));
    }

    if (isNotEmpty(input.getDelegateSelectors())) {
      customSecretsManagerConfig.setDelegateSelectors(input.getDelegateSelectors());
    }

    if (isNotEmpty(input.getCommandPath())) {
      customSecretsManagerConfig.setCommandPath(input.getCommandPath());
    }

    if (isNotEmpty(input.getConnectorId())) {
      customSecretsManagerConfig.setConnectorId(input.getConnectorId());
    }

    if (isNotEmpty(input.getHost())) {
      customSecretsManagerConfig.setHost(input.getHost());
    }

    if (input.getUsageScope() != null) {
      customSecretsManagerConfig.setUsageRestrictions(
          usageScopeController.populateUsageRestrictions(input.getUsageScope(), accountId));
    }
  }
}
