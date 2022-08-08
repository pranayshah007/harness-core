/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.security.customsecretsmanager.CustomSecretsManagerValidationUtils.buildShellScriptParameters;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.encryptors.CustomEncryptor;
import io.harness.exception.CommandExecutionException;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.shell.ShellExecutionData;

import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.delegatetasks.ShellScriptTaskHandler;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@OwnedBy(PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
@Singleton
public class CustomSecretsManagerEncryptor implements CustomEncryptor {
  private final ShellScriptTaskHandler shellScriptTaskHandler;
  private static final String OUTPUT_VARIABLE = "secret";
  private final TimeLimiter timeLimiter;

  @Inject
  public CustomSecretsManagerEncryptor(TimeLimiter timeLimiter, ShellScriptTaskHandler shellScriptTaskHandler) {
    this.shellScriptTaskHandler = shellScriptTaskHandler;
    this.timeLimiter = timeLimiter;
  }

  @Override
  public boolean validateReference(
      String accountId, Set<EncryptedDataParams> params, EncryptionConfig encryptionConfig) {
    return isNotEmpty(
        fetchSecretValue(accountId, EncryptedRecordData.builder().parameters(params).build(), encryptionConfig));
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    CustomSecretsManagerConfig customSecretsManagerConfig = (CustomSecretsManagerConfig) encryptionConfig;
    final int NUM_OF_RETRIES = 3;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
            () -> fetchSecretValueInternal(encryptedRecord, customSecretsManagerConfig));
      } catch (SecretManagementDelegateException e) {
        throw e;
      } catch (Exception e) {
        failedAttempts++;
        if (failedAttempts == NUM_OF_RETRIES) {
          String message = "Faild to decrypt " + encryptedRecord.getName() + " after " + NUM_OF_RETRIES + " retries";
          throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR, message, e, USER);
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private char[] fetchSecretValueInternal(
      EncryptedRecord encryptedRecord, CustomSecretsManagerConfig customSecretsManagerConfig) {
    ShellScriptParameters shellScriptParameters = buildShellScriptParameters(customSecretsManagerConfig);
    CommandExecutionResult commandExecutionResult = shellScriptTaskHandler.handle(shellScriptParameters);
    if (commandExecutionResult.getStatus() != SUCCESS) {
      String errorMessage = "Could not retrieve secret with the given parameters due to error in shell script.";
      throw new CommandExecutionException(errorMessage);
    }
    ShellExecutionData shellExecutionData = (ShellExecutionData) commandExecutionResult.getCommandExecutionData();
    String result = shellExecutionData.getSweepingOutputEnvVariables().get(OUTPUT_VARIABLE);
    if (isEmpty(result) || result.equals("null")) {
      String errorMessage = "Empty or null value returned by custom shell script for the given secret: "
          + encryptedRecord.getName() + ", for accountId: " + customSecretsManagerConfig.getAccountId();
      throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR, errorMessage, USER);
    }
    return result.toCharArray();
  }
}
