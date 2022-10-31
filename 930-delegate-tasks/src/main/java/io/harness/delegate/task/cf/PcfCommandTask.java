/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.cf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandTaskParameters;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequest;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PcfCommandTask extends AbstractDelegateRunnableTask {
  @Inject PcfDelegateTaskHelper pcfDelegateTaskHelper;

  public PcfCommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public CfCommandExecutionResponse run(TaskParameters parameters) {
    if (parameters instanceof CfCommandTaskParameters) {
      return run(new TaskParameters[] {parameters});
    } else if (!(parameters instanceof CfRunPluginCommandRequest)) {
      throw new InvalidArgumentsException(Pair.of("cfCommandRequest", "Must be instance of CfPluginCommandRequest"));
    }
    final CfRunPluginCommandRequest pluginCommandRequest = (CfRunPluginCommandRequest) parameters;
    return getPcfCommandExecutionResponse(pluginCommandRequest, pluginCommandRequest.getEncryptedDataDetails());
  }

  @Override
  public CfCommandExecutionResponse run(Object[] parameters) {
    final CfCommandRequest cfCommandRequest;
    final List<EncryptedDataDetail> encryptedDataDetails;
    if (parameters[0] instanceof CfCommandTaskParameters) {
      CfCommandTaskParameters cfCommandTaskParameters = (CfCommandTaskParameters) parameters[0];
      cfCommandRequest = cfCommandTaskParameters.getPcfCommandRequest();
      encryptedDataDetails = cfCommandTaskParameters.getEncryptedDataDetails();
    } else {
      cfCommandRequest = (CfCommandRequest) parameters[0];
      encryptedDataDetails = (List<EncryptedDataDetail>) parameters[1];
    }
    return getPcfCommandExecutionResponse(cfCommandRequest, encryptedDataDetails);
  }

  private CfCommandExecutionResponse getPcfCommandExecutionResponse(
      CfCommandRequest cfCommandRequest, List<EncryptedDataDetail> encryptedDataDetails) {
    boolean isInstanceSync = cfCommandRequest instanceof CfInstanceSyncRequest;
    return pcfDelegateTaskHelper.getPcfCommandExecutionResponse(
        cfCommandRequest, encryptedDataDetails, isInstanceSync, getLogStreamingTaskClient());
  }
}
