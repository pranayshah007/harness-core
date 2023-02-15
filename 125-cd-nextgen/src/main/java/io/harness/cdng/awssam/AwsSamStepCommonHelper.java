/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.awssam;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsSpecParameters;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.awssam.request.AwsSamCommandRequest;
import io.harness.delegate.task.awssam.response.AwsSamCommandResponse;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsSamStepCommonHelper extends CDStepHelper {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public TaskChainResponse queueTask(StepElementParameters stepElementParameters,
      AwsSamCommandRequest awsSamCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd,
      TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {awsSamCommandRequest})
                            .taskType(taskType.toString())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName = taskType.getDisplayName() + " : " + awsSamCommandRequest.getCommandName();
    GoogleFunctionsSpecParameters googleFunctionsSpecParameters =
        (GoogleFunctionsSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, googleFunctionsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(googleFunctionsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public static String getErrorMessage(AwsSamCommandResponse awsSamCommandResponse) {
    return awsSamCommandResponse.getErrorMessage() == null ? "" : awsSamCommandResponse.getErrorMessage();
  }
}
