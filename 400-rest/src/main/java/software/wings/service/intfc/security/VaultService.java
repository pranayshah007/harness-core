/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretChangeLog;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.VaultConfig;
import software.wings.beans.alert.KmsSetupAlert;

import java.util.List;

/**
 * Created by rsingh on 11/2/17.
 */
@OwnedBy(PL)
@TargetModule(HarnessModule._890_SM_CORE)
public interface VaultService {
  String saveOrUpdateVaultConfig(String accountId, VaultConfig vaultConfig, boolean validateBySavingTestSecret);

  boolean deleteVaultConfig(String accountId, String vaultConfigId);

  List<SecretEngineSummary> listSecretEngines(VaultConfig vaultConfig);

  void decryptVaultConfigSecrets(String accountId, VaultConfig vaultConfig, boolean maskSecret);

  VaultConfig getVaultConfigByName(String accountId, String name);

  void renewToken(BaseVaultConfig baseVaultConfig);

  void renewAppRoleClientToken(BaseVaultConfig baseVaultConfig);

  List<SecretChangeLog> getVaultSecretChangeLogs(EncryptedData encryptedData, VaultConfig vaultConfig);

  KmsSetupAlert getRenewalAlert(BaseVaultConfig baseVaultConfig);

  void validateVaultConfig(String accountId, VaultConfig vaultConfig);

  void validateVaultConfig(String accountId, VaultConfig vaultConfig, boolean validateBySavingDummySecret);

  VaultAppRoleLoginResult appRoleLogin(BaseVaultConfig baseVaultConfig);

  VaultConfig getVaultConfig(String accountId, String entityId);
}
