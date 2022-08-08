/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.security;

import static io.harness.annotations.dev.HarnessModule._890_SM_CORE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretChangeLog;
import io.harness.helpers.ext.vault.SSHVaultAuthResult;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.security.encryption.EncryptedRecord;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.CyberArkConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.TaskType;
import software.wings.beans.VaultConfig;
import software.wings.delegatetasks.DelegateTaskType;

import java.util.List;

/**
 * Created by rsingh on 10/2/17.
 */
@OwnedBy(PL)
@TargetModule(_890_SM_CORE)
public interface SecretManagementDelegateService {
  int NUM_OF_RETRIES = 3;

  /**
   * Retrieve the versions metadata for Vault managed secrets from Hashicorp Vault, and construct the version history as
   * {@see SecretChangeLog} to be displayed in Harness UI.
   */
  @DelegateTaskType(TaskType.VAULT_GET_CHANGELOG)
  List<SecretChangeLog> getVaultSecretChangeLogs(EncryptedRecord encryptedData, VaultConfig vaultConfig);

  /**
   * Renew the Hashicorp Vault authentication token.
   */
  @DelegateTaskType(TaskType.VAULT_RENEW_TOKEN) boolean renewVaultToken(BaseVaultConfig vaultConfig);

  /**
   * List vault secret engines
   */
  @DelegateTaskType(TaskType.VAULT_LIST_ENGINES)
  List<SecretEngineSummary> listSecretEngines(BaseVaultConfig vaultConfig);
  /**
   * Login Vault using AppRole auth.
   */
  @DelegateTaskType(TaskType.VAULT_APPROLE_LOGIN) VaultAppRoleLoginResult appRoleLogin(BaseVaultConfig vaultConfig);

  @DelegateTaskType(TaskType.SSH_SECRET_ENGINE_AUTH) SSHVaultAuthResult validateSSHVault(SSHVaultConfig vaultConfig);

  /**
   * Validate the CyberArk configuration, including the connectivity to CyberArk service, client certificate etc.
   */
  @DelegateTaskType(TaskType.CYBERARK_VALIDATE_CONFIG) boolean validateCyberArkConfig(CyberArkConfig cyberArkConfig);

  @DelegateTaskType(TaskType.VAULT_SIGN_PUBLIC_KEY_SSH)
  void signPublicKey(HostConnectionAttributes hostConnectionAttributes, SSHVaultConfig sshVaultConfig);
}
