/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaCommandRequest;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsLambdaHelper extends CDStepHelper {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private AwsLambdaEntityHelper awsLambdaEntityHelper;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public AwsLambdaFunctionsInfraConfig getInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    return awsLambdaEntityHelper.getInfraConfig(infrastructure, AmbianceUtils.getNgAccess(ambiance));
  }

  public TaskChainResponse queueTask(StepElementParameters stepElementParameters,
      AwsLambdaCommandRequest awsLambdaCommandRequest, Ambiance ambiance, PassThroughData passThroughData,
      boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {awsLambdaCommandRequest})
                            .taskType(TaskType.AWS_LAMBDA_COMMAND_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName =
        TaskType.AWS_LAMBDA_COMMAND_TASK_NG.getDisplayName() + " : " + awsLambdaCommandRequest.getCommandName();
    AwsLambdaSpecParameters awsLambdaSpecParameters = (AwsLambdaSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, awsLambdaSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(awsLambdaSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }
}
