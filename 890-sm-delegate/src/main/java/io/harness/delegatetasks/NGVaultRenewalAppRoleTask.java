/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.helpers.NGVaultTaskHelper.getVaultAppRoleLoginResult;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import software.wings.beans.BaseVaultConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class NGVaultRenewalAppRoleTask extends AbstractDelegateRunnableTask {
  public NGVaultRenewalAppRoleTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
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

  private NGVaultRenewalAppRoleTaskResponse run(NGVaultRenewalTaskParameters ngVaultRenewalTaskParameters) {
    return runInternal(ngVaultRenewalTaskParameters);
  }

  protected static NGVaultRenewalAppRoleTaskResponse runInternal(
      NGVaultRenewalTaskParameters ngVaultRenewalTaskParameters) {
    BaseVaultConfig vaultConfig = ngVaultRenewalTaskParameters.getEncryptionConfig();
    log.info("Executing NGVaultRenewalAppRoleTask for: " + vaultConfig.getName());
    return NGVaultRenewalAppRoleTaskResponse.builder()
        .vaultAppRoleLoginResult(getVaultAppRoleLoginResult(vaultConfig))
        .build();
  }
}
