/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.VaultConfig;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.secretManager.QLCreateSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLHashicorpVaultAuthDetails;
import software.wings.graphql.schema.mutation.secretManager.QLHashicorpVaultSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLUpdateHashicorpVaultInput;
import software.wings.graphql.schema.mutation.secretManager.QLUpdateSecretManagerInput;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager.QLSecretManagerBuilder;
import software.wings.graphql.schema.type.secretManagers.QLSecretManagerType;
import software.wings.service.intfc.security.VaultService;

import com.google.inject.Inject;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(HarnessTeam.PL)
public class HashicorpVaultDataFetcher implements SecretManagerMutationDataFetcher {
  @Inject private VaultService vaultService;
  @Inject private SecretManagerController secretManagerController;
  @Inject private UsageScopeController usageScopeController;

  @Override
  public QLSecretManager createSecretManager(QLCreateSecretManagerInput input, String accountId) {
    QLHashicorpVaultSecretManagerInput hashicorpVaultConfig = input.getHashicorpVaultConfigInput();

    if (hashicorpVaultConfig == null) {
      throw new InvalidRequestException("Hashicorp vault config is not provided");
    }
    VaultConfig vaultConfig = createVaultConfig(hashicorpVaultConfig, accountId);
    String uuid = vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    SecretManagerConfig secretManagerConfig = vaultService.getVaultConfig(accountId, uuid);

    final QLSecretManagerBuilder builder = QLSecretManager.builder();
    secretManagerController.populateSecretManager(secretManagerConfig, builder);
    return builder.build();
  }

  @Override
  public QLSecretManager updateSecretManager(QLUpdateSecretManagerInput input, String accountId) {
    String secretManagerId = input.getSecretManagerId();
    QLUpdateHashicorpVaultInput hashicorpVaultConfig = input.getHashicorpVaultConfigInput();

    if (hashicorpVaultConfig == null) {
      throw new InvalidRequestException("Hashicorp vault config is not provided");
    }
    VaultConfig vaultConfig;
    try {
      vaultConfig = vaultService.getVaultConfig(accountId, secretManagerId);
    } catch (ClassCastException ex) {
      throw new InvalidRequestException(
          "Secret manager with given id is not of type " + QLSecretManagerType.HASHICORP_VAULT);
    }

    if (vaultConfig == null) {
      throw new InvalidRequestException("Secret manager with given id doesn't exist");
    }

    updateVaultConfig(vaultConfig, hashicorpVaultConfig, accountId);

    vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true);

    SecretManagerConfig secretManagerConfig = vaultService.getVaultConfig(accountId, secretManagerId);

    final QLSecretManagerBuilder builder = QLSecretManager.builder();
    secretManagerController.populateSecretManager(secretManagerConfig, builder);
    return builder.build();
  }

  @Override
  public void deleteSecretManager(String accountId, String secretManagerId) {
    vaultService.deleteVaultConfig(accountId, secretManagerId);
  }

  private void updateVaultConfig(VaultConfig vaultConfig, QLUpdateHashicorpVaultInput input, String accountId) {
    if (input.getName() != null) {
      vaultConfig.setName(input.getName());
    }

    if (input.getAuthDetails() != null) {
      resolveAuthType(vaultConfig, input.getAuthDetails());
    }

    if (input.getIsDefault() != null) {
      vaultConfig.setDefault(input.getIsDefault());
    }

    if (input.getSecretEngineRenewalInterval() != null) {
      vaultConfig.setRenewalInterval(input.getSecretEngineRenewalInterval());
    }

    if (input.getIsReadOnly() != null) {
      vaultConfig.setReadOnly(input.getIsReadOnly());
    }

    if (input.getUsageScope() != null) {
      vaultConfig.setUsageRestrictions(
          usageScopeController.populateUsageRestrictions(input.getUsageScope(), accountId));
    }

    vaultConfig.setNamespace(input.getNamespace());
  }

  private VaultConfig createVaultConfig(QLHashicorpVaultSecretManagerInput input, String accountId) {
    VaultConfig vaultConfig = new VaultConfig();
    vaultConfig.setName(input.getName());
    vaultConfig.setEncryptionType(EncryptionType.VAULT);
    vaultConfig.setEngineManuallyEntered(Boolean.TRUE);
    vaultConfig.setScopedToAccount(Boolean.FALSE);
    vaultConfig.setVaultUrl(input.getVaultUrl());
    vaultConfig.setBasePath(input.getBasePath());
    vaultConfig.setReadOnly(input.isReadOnly());
    vaultConfig.setDefault(input.isDefault());
    vaultConfig.setSecretEngineName(input.getSecretEngineName());
    vaultConfig.setSecretEngineVersion(input.getSecretEngineVersion());
    vaultConfig.setRenewalInterval(input.getSecretEngineRenewalInterval());
    vaultConfig.setUsageRestrictions(usageScopeController.populateUsageRestrictions(input.getUsageScope(), accountId));
    vaultConfig.setNamespace(input.getNamespace());

    resolveAuthType(vaultConfig, input.getAuthDetails());

    return vaultConfig;
  }

  private void resolveAuthType(VaultConfig vaultConfig, QLHashicorpVaultAuthDetails authDetails) {
    String authToken = authDetails.getAuthToken();
    if (authToken != null) {
      vaultConfig.setAuthToken(authToken);
      vaultConfig.setAppRoleId(null);
      vaultConfig.setSecretId(null);
    } else {
      String appRoleId = authDetails.getAppRoleId();
      String secretId = authDetails.getSecretId();
      if (appRoleId == null || secretId == null) {
        throw new InvalidRequestException("Insufficient authentication details");
      }
      vaultConfig.setAppRoleId(appRoleId);
      vaultConfig.setSecretId(secretId);
      vaultConfig.setAuthToken(null);
    }
  }
}
