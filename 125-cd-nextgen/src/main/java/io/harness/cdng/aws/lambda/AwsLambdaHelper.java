/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaCommandRequest;
import io.harness.exception.InvalidRequestException;
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

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
                            .taskType(TaskType.AWS_LAMBDA_DEPLOY_COMMAND_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName =
        TaskType.AWS_LAMBDA_DEPLOY_COMMAND_TASK_NG.getDisplayName() + " : " + awsLambdaCommandRequest.getCommandName();
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

  public ManifestOutcome getAwsLambdaManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    // Filter only Aws Lambda supported manifest types
    List<ManifestOutcome> awsLambdaManifests =
        manifestOutcomes.stream()
            .filter(
                manifestOutcome -> ManifestType.AWS_LAMBDA_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
            .collect(Collectors.toList());

    // Check if Aws Lambda Manifests are empty
    if (isEmpty(awsLambdaManifests)) {
      throw new InvalidRequestException("Aws Lambda Manifest is mandatory.", USER);
    }
    return awsLambdaManifests.get(0);
  }
}
