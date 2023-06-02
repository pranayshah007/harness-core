/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import software.wings.beans.NewRelicDeploymentMarkerPayload;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class NewRelicDeploymentMarkerTask extends AbstractDelegateRunnableTask {
  @Inject private NewRelicDelegateService newRelicDelegateService;

  public NewRelicDeploymentMarkerTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    NewRelicDataCollectionInfo dataCollectionInfo = (NewRelicDataCollectionInfo) parameters;
    if (isEmpty(dataCollectionInfo.getDeploymentMarker())) {
      throw new WingsException("Empty deployment marker body");
    }
    try {
      NewRelicDeploymentMarkerPayload payload =
          JsonUtils.asObject(dataCollectionInfo.getDeploymentMarker(), NewRelicDeploymentMarkerPayload.class);
      newRelicDelegateService.postDeploymentMarker(dataCollectionInfo.getNewRelicConfig(),
          dataCollectionInfo.getEncryptedDataDetails(), dataCollectionInfo.getNewRelicAppId(), payload,
          ThirdPartyApiCallLog.builder()
              .accountId(getAccountId())
              .delegateId(getDelegateId())
              .delegateTaskId(getTaskId())
              .stateExecutionId(dataCollectionInfo.getStateExecutionId())
              .build());
      return DataCollectionTaskResult.builder()
          .status(DataCollectionTaskResult.DataCollectionTaskStatus.SUCCESS)
          .newRelicDeploymentMarkerBody(dataCollectionInfo.getDeploymentMarker())
          .build();
    } catch (Exception ex) {
      return DataCollectionTaskResult.builder()
          .status(DataCollectionTaskResult.DataCollectionTaskStatus.FAILURE)
          .stateType(DelegateStateType.NEW_RELIC)
          .errorMessage("Could not send deployment marker : " + ExceptionUtils.getMessage(ex))
          .newRelicDeploymentMarkerBody(dataCollectionInfo.getDeploymentMarker())
          .build();
    }
  }
}
