/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.security.customsecretsmanager.CustomSecretsManagerValidationUtils.buildShellScriptTaskParametersNG;

import static java.time.Duration.ofMillis;

import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.encryptors.CustomEncryptor;
import io.harness.exception.CommandExecutionException;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.encryption.*;
import io.harness.shell.ShellExecutionData;

import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.delegatetasks.ShellScriptTaskHandlerNG;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.Set;

public class NGCustomSecretManagerEncryptor implements CustomEncryptor {
  private final ShellScriptTaskHandlerNG shellScriptTaskHandlerNG;

  private static final String OUTPUT_VARIABLE = "secret";
  private final TimeLimiter timeLimiter;

  @Inject
  public NGCustomSecretManagerEncryptor(TimeLimiter timeLimiter, ShellScriptTaskHandlerNG shellScriptTaskHandlerNG) {
    this.timeLimiter = timeLimiter;
    this.shellScriptTaskHandlerNG = shellScriptTaskHandlerNG;
  }

  @Override
  public boolean validateReference(
      String accountId, Set<EncryptedDataParams> params, EncryptionConfig encryptionConfig) {
    return isNotEmpty(
        fetchSecretValue(accountId, EncryptedRecordData.builder().parameters(params).build(), encryptionConfig));
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    CustomSecretNGManagerConfig customSecretsManagerConfig = (CustomSecretNGManagerConfig) encryptionConfig;
    final int NUM_OF_RETRIES = 3;
    int failedAttempts = 0;
    while (true) {
      try {
        return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20),
            () -> fetchSecretValueInternal(accountId, encryptedRecord, customSecretsManagerConfig));
      } catch (SecretManagementDelegateException e) {
        throw e;
      } catch (Exception e) {
        failedAttempts++;
        if (failedAttempts == NUM_OF_RETRIES) {
          String message = "Failed to decrypt " + encryptedRecord.getName() + " after " + NUM_OF_RETRIES + " retries";
          throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR, message, e, USER);
        }
        sleep(ofMillis(1000));
      }
    }
  }

  private char[] fetchSecretValueInternal(
      String accountId, EncryptedRecord encryptedRecord, CustomSecretNGManagerConfig customSecretNGManagerConfig) {
    ShellScriptTaskParametersNG shellScriptTaskParametersNG =
        buildShellScriptTaskParametersNG(accountId, encryptedRecord, customSecretNGManagerConfig);
    ShellScriptTaskResponseNG shellScriptTaskResponseNG =
        (ShellScriptTaskResponseNG) shellScriptTaskHandlerNG.handle(shellScriptTaskParametersNG, null);
    if (shellScriptTaskResponseNG.getStatus() != SUCCESS) {
      String errorMessage = "Could not retrieve secret with the given parameters due to error in shell script.";
      throw new CommandExecutionException(errorMessage);
    }
    ShellExecutionData shellExecutionData =
        (ShellExecutionData) shellScriptTaskResponseNG.getExecuteCommandResponse().getCommandExecutionData();
    String result = shellExecutionData.getSweepingOutputEnvVariables().get(OUTPUT_VARIABLE);
    if (isEmpty(result) || result.equals("null")) {
      String errorMessage = "Empty or null value returned by custom shell script for the given secret: "
          + encryptedRecord.getName() + ", for accountId: " + accountId;
      throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR, errorMessage, USER);
    }
    return result.toCharArray();
  }
}
