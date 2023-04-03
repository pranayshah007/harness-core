/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;
import static io.harness.shell.SshSessionFactory.getSSHSession;

import static software.wings.utils.SshDelegateHelperUtils.populateBuilderWithCredentials;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.CapabilityResponse.CapabilityResponseBuilder;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.SshFactory;

import software.wings.beans.BastionConnectionAttributes;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.validation.capabilities.SSHHostValidationCapability;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SSHHostValidationCapabilityCheck implements CapabilityCheck {
  @Inject private EncryptionService encryptionService;
  @Inject private SecretManagementDelegateService secretManagementDelegateService;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    SSHHostValidationCapability capability = (SSHHostValidationCapability) delegateCapability;
    CapabilityResponseBuilder capabilityResponseBuilder = CapabilityResponse.builder().delegateCapability(capability);

    if (capability.getValidationInfo().isExecuteOnDelegate()) {
      return capabilityResponseBuilder.validated(true).build();
    }

    decryptCredentials(capability.getHostConnectionAttributes(), capability.getBastionConnectionAttributes(),
        capability.getHostConnectionCredentials(), capability.getBastionConnectionCredentials(),
        capability.getSshVaultConfig());
    try {
      SshSessionConfig hostConnectionTest = createSshSessionConfig(capability);
      int timeout = (int) ofSeconds(60L).toMillis();
      int sshConnectionTimeout = (int) ofSeconds(120L).toMillis();
      hostConnectionTest.setSocketConnectTimeout(timeout);
      hostConnectionTest.setSshConnectionTimeout(sshConnectionTimeout);
      hostConnectionTest.setSshSessionTimeout(timeout);
      performTest(hostConnectionTest);
      capabilityResponseBuilder.validated(true);
    } catch (Exception e) {
      log.error("Failed to validate host - public dns:" + capability.getValidationInfo().getPublicDns(),
          ExceptionMessageSanitizer.sanitizeException(e));
      capabilityResponseBuilder.validated(false);
    }
    return capabilityResponseBuilder.build();
  }

  @VisibleForTesting
  void performTest(SshSessionConfig hostConnectionTest) throws Exception {
    if (hostConnectionTest.isUseSshClient()) {
      SshFactory.getSshClient(hostConnectionTest).testConnection();
    } else {
      getSSHSession(hostConnectionTest).disconnect();
    }
  }

  private void decryptCredentials(SettingAttribute hostConnectionAttributes,
      SettingAttribute bastionConnectionAttributes, List<EncryptedDataDetail> hostConnectionCredential,
      List<EncryptedDataDetail> bastionConnectionCredential, SSHVaultConfig sshVaultConfig) {
    if (hostConnectionAttributes != null) {
      encryptionService.decrypt(
          (HostConnectionAttributes) hostConnectionAttributes.getValue(), hostConnectionCredential, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (HostConnectionAttributes) hostConnectionAttributes.getValue(), hostConnectionCredential);
      if (hostConnectionAttributes.getValue() instanceof HostConnectionAttributes
          && ((HostConnectionAttributes) hostConnectionAttributes.getValue()).isVaultSSH()) {
        secretManagementDelegateService.signPublicKey(
            (HostConnectionAttributes) hostConnectionAttributes.getValue(), sshVaultConfig);
      }
    }
    if (bastionConnectionAttributes != null) {
      encryptionService.decrypt(
          (BastionConnectionAttributes) bastionConnectionAttributes.getValue(), bastionConnectionCredential, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (BastionConnectionAttributes) bastionConnectionAttributes.getValue(), bastionConnectionCredential);
    }
  }

  public static SshSessionConfig createSshSessionConfig(SSHHostValidationCapability capability) {
    SshSessionConfig.Builder builder = aSshSessionConfig()
                                           .withAccountId(capability.getValidationInfo().getAccountId())
                                           .withAppId(capability.getValidationInfo().getAppId())
                                           .withExecutionId(capability.getValidationInfo().getAccountId())
                                           .withHost(capability.getValidationInfo().getPublicDns())
                                           .withCommandUnitName("HOST_CONNECTION_TEST");

    // TODO: The following can be removed as we do not support username and password from context anymore
    SSHExecutionCredential sshExecutionCredential = capability.getSshExecutionCredential();
    if (sshExecutionCredential != null) {
      builder.withUserName(sshExecutionCredential.getSshUser())
          .withPassword(sshExecutionCredential.getSshPassword())
          .withSudoAppName(sshExecutionCredential.getAppAccount())
          .withSudoAppPassword(sshExecutionCredential.getAppAccountPassword());
    }

    populateBuilderWithCredentials(
        builder, capability.getHostConnectionAttributes(), capability.getBastionConnectionAttributes());
    return builder.build();
  }
}
