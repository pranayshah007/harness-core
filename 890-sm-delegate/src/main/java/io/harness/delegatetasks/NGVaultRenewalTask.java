/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.helpers.NGVaultTaskHelper.logAndThrowVaultError;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.SecretManagementDelegateException;

import software.wings.beans.BaseVaultConfig;
import software.wings.helpers.ext.vault.VaultRestClientFactory;
import software.wings.helpers.ext.vault.VaultSysAuthRestClient;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@OwnedBy(PL)
@Slf4j
public class NGVaultRenewalTask extends AbstractDelegateRunnableTask {
  private static int NUM_OF_RETRIES = 3;

  public NGVaultRenewalTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return run((NGVaultRenewalTaskParameters) parameters[0]);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    return run((NGVaultRenewalTaskParameters) parameters);
  }

  private NGVaultRenewalTaskResponse run(NGVaultRenewalTaskParameters ngVaultRenewalTaskParameters) {
    return runInternal(ngVaultRenewalTaskParameters);
  }

  protected static NGVaultRenewalTaskResponse runInternal(NGVaultRenewalTaskParameters ngVaultRenewalTaskParameters) {
    BaseVaultConfig baseVaultConfig = ngVaultRenewalTaskParameters.getEncryptionConfig();
    log.info("Executing NGVaultRenewalTask for: " + baseVaultConfig.getName());
    int failedAttempts = 0;
    while (true) {
      try {
        log.info("ng renewing token for vault {}", baseVaultConfig);
        VaultSysAuthRestClient restClient =
            VaultRestClientFactory
                .getVaultRetrofit(baseVaultConfig.getVaultUrl(), baseVaultConfig.isCertValidationRequired())
                .create(VaultSysAuthRestClient.class);
        Response<Object> response =
            restClient.renewToken(baseVaultConfig.getAuthToken(), baseVaultConfig.getNamespace()).execute();
        boolean isSuccessful = response.isSuccessful();
        if (isSuccessful) {
          return NGVaultRenewalTaskResponse.builder().isSuccessful(true).build();
        } else {
          logAndThrowVaultError(baseVaultConfig, response, "renew");
        }
      } catch (Exception e) {
        failedAttempts++;
        if (failedAttempts == NUM_OF_RETRIES) {
          String message = "renewal failed after " + NUM_OF_RETRIES + " retries";
          throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
        }
        sleep(ofMillis(1000));
      }
    }
  }
}
