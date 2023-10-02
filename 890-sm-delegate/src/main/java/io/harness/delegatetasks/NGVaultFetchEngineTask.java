/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.helpers.vault.NGVaultTaskHelper.getToken;
import static io.harness.helpers.vault.NGVaultTaskHelper.getVaultAppRoleToken;
import static io.harness.helpers.vault.NGVaultTaskHelper.logAndThrowVaultError;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.vault.SecretEngineSummary;

import software.wings.beans.BaseVaultConfig;
import software.wings.helpers.ext.vault.SysMount;
import software.wings.helpers.ext.vault.SysMountsResponse;
import software.wings.helpers.ext.vault.VaultRestClientFactory;
import software.wings.helpers.ext.vault.VaultSysAuthRestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@OwnedBy(PL)
@Slf4j
public class NGVaultFetchEngineTask extends AbstractDelegateRunnableTask {
  public NGVaultFetchEngineTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    NGVaultRenewalTaskParameters ngVaultRenewalTaskParameters = (NGVaultRenewalTaskParameters) parameters;
    return runInternal(ngVaultRenewalTaskParameters);
  }

  protected static NGVaultFetchEngineTaskResponse runInternal(
      NGVaultRenewalTaskParameters ngVaultRenewalTaskParameters) {
    BaseVaultConfig vaultConfig = ngVaultRenewalTaskParameters.getEncryptionConfig();
    log.info("Executing NGVaultFetchEngineTask for: " + vaultConfig.getName());
    List<SecretEngineSummary> secretEngineSummaries = new ArrayList<>();
    try {
      String vaultToken = getToken(vaultConfig);
      if (isEmpty(vaultToken)) {
        vaultToken = getVaultAppRoleToken(vaultConfig);
        vaultConfig.setAuthToken(vaultToken);
      }

      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);
      Response<SysMountsResponse> response =
          restClient.getAllMounts(vaultConfig.getAuthToken(), vaultConfig.getNamespace()).execute();
      if (response.isSuccessful()) {
        Map<String, SysMount> sysMountMap = response.body().getData();
        log.info("NG: Found Vault sys mount points: {}", sysMountMap.keySet());

        for (Entry<String, SysMount> entry : sysMountMap.entrySet()) {
          String secretEngineName = StringUtils.removeEnd(entry.getKey(), "/");
          SysMount sysMount = entry.getValue();
          Integer version = sysMount.getOptions() == null ? null : sysMount.getOptions().getVersion();
          SecretEngineSummary secretEngineSummary = SecretEngineSummary.builder()
                                                        .name(secretEngineName)
                                                        .description(sysMount.getDescription())
                                                        .type(sysMount.getType())
                                                        .version(version)
                                                        .build();
          secretEngineSummaries.add(secretEngineSummary);
        }
      } else {
        // Throw error when sys mount fails.
        logAndThrowVaultError(vaultConfig, response, "listSecretEngines");
      }
    } catch (IOException e) {
      String message =
          String.format("Failed to list secret engines for %s due to unexpected network error. Please try again.",
              vaultConfig.getVaultUrl());
      log.error(message, e);
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, USER);
    }

    return NGVaultFetchEngineTaskResponse.builder().secretEngineSummaryList(secretEngineSummaries).build();
  }
}
