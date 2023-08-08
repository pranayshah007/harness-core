/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.TaskType.FETCH_INSTANCE_SCRIPT_TASK_NG;
import static software.wings.sm.states.customdeploymentng.InstanceMapperUtils.getHostnameFieldName;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.HostOutcome;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.cdng.ssh.output.HostsOutput;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNG;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGRequest;
import io.harness.delegate.task.customdeployment.FetchInstanceScriptTaskNGResponse;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.yaml.utils.NGVariablesUtils;

import software.wings.beans.TaskType;
import software.wings.sm.states.customdeploymentng.InstanceMapperUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class FetchInstanceScriptStep extends CdTaskExecutable<FetchInstanceScriptTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.FETCH_INSTANCE_SCRIPT.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String OUTPUT_PATH_KEY = "INSTANCE_OUTPUT_PATH";
  public static final String WORKING_DIRECTORY = "/tmp";
  public static final String INSTANCE_NAME = "instancename";
  @Inject private CDStepHelper cdStepHelper;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  static Function<InstanceMapperUtils.HostProperties, CustomDeploymentServerInstanceInfo> instanceElementMapper =
      hostProperties -> {
    return CustomDeploymentServerInstanceInfo.builder()
        .instanceId(UUIDGenerator.generateUuid())
        .instanceName(hostProperties.getHostName())
        .properties(hostProperties.getOtherPropeties())
        .build();
  };

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_SVC_ENV_REDESIGN)) {
      throw new AccessDeniedException(
          "NG_SVC_ENV_REDESIGN FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  private static String findSecretIdentifierByScope(SecretRefData secret) {
    if (secret.getScope() == Scope.PROJECT) {
      return secret.getIdentifier();
    } else if (secret.getScope() == Scope.ORG) {
      return "org." + secret.getIdentifier();
    } else if (secret.getScope() == Scope.ACCOUNT) {
      return "account." + secret.getIdentifier();
    } else {
      throw new RuntimeException("Invalid scope for a secret");
    }
  }
  private String getResolvedFetchInstanceScript(
      Ambiance ambiance, CustomDeploymentInfrastructureOutcome infrastructureOutcome) {
    List<String> expressions =
        EngineExpressionEvaluator.findExpressions(infrastructureOutcome.getInstanceFetchScript());
    String fetchInstanceScript = infrastructureOutcome.getInstanceFetchScript();
    for (Map.Entry<String, Object> entry : infrastructureOutcome.getVariables().entrySet()) {
      if (entry.getValue() instanceof SecretRefData) {
        Optional<String> exprToReplace =
            expressions.stream().filter(expr -> expr.contains(entry.getKey() + ">")).findFirst();
        if (exprToReplace.isPresent()) {
          fetchInstanceScript = fetchInstanceScript.replace(exprToReplace.get(),
              NGVariablesUtils.fetchSecretExpression(findSecretIdentifierByScope((SecretRefData) entry.getValue())));
        }
      }
    }
    return (String) cdExpressionResolver.updateExpressions(ambiance, fetchInstanceScript);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    FetchInstanceScriptStepParameters stepSpec = (FetchInstanceScriptStepParameters) stepParameters.getSpec();
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);

    CustomDeploymentInfrastructureOutcome infrastructureOutcome =
        (CustomDeploymentInfrastructureOutcome) cdStepHelper.getInfrastructureOutcome(ambiance);

    FetchInstanceScriptTaskNGRequest taskParameters =
        FetchInstanceScriptTaskNGRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .executionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .scriptBody(getResolvedFetchInstanceScript(ambiance, infrastructureOutcome))
            .variables(new HashMap<>())
            .outputPathKey(OUTPUT_PATH_KEY)
            .timeoutInMillis(CDStepHelper.getTimeoutInMillis(stepParameters))
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(TaskType.FETCH_INSTANCE_SCRIPT_TASK_NG.name())
                                  .parameters(new Object[] {taskParameters})
                                  .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(FetchInstanceScriptTaskNG.COMMAND_UNIT),
        FETCH_INSTANCE_SCRIPT_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepSpec.getDelegateSelectors()), stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<FetchInstanceScriptTaskNGResponse> responseDataSupplier)
      throws Exception {
    try {
      FetchInstanceScriptTaskNGResponse response;
      try {
        response = responseDataSupplier.get();
      } catch (Exception ex) {
        log.error("Error while processing Fetch Instance script task response: {}", ExceptionUtils.getMessage(ex), ex);
        throw ex;
      }
      if (response.getCommandExecutionStatus() != SUCCESS) {
        return StepResponse.builder()
            .unitProgressList(response.getUnitProgressData().getUnitProgresses())
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
            .build();
      }
      StepResponseBuilder builder = StepResponse.builder()
                                        .unitProgressList(response.getUnitProgressData().getUnitProgresses())
                                        .status(Status.SUCCEEDED);
      List<CustomDeploymentServerInstanceInfo> instanceElements = new ArrayList<>();
      CustomDeploymentInfrastructureOutcome infrastructureOutcome =
          (CustomDeploymentInfrastructureOutcome) cdStepHelper.getInfrastructureOutcome(ambiance);
      instanceElements =
          InstanceMapperUtils.mapJsonToInstanceElements(INSTANCE_NAME, infrastructureOutcome.getInstanceAttributes(),
              infrastructureOutcome.getInstancesListPath(), response.getOutput(), instanceElementMapper);
      instanceElements.forEach(serverInstanceInfo -> {
        serverInstanceInfo.setInstanceFetchScript(getResolvedFetchInstanceScript(ambiance, infrastructureOutcome));
        serverInstanceInfo.setInfrastructureKey(infrastructureOutcome.getInfrastructureKey());
      });
      validateInstanceElements(instanceElements, infrastructureOutcome);
      StepResponse.StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance,
          instanceElements.stream().map(element -> (ServerInstanceInfo) element).collect(Collectors.toList()));
      InstancesOutcome instancesOutcome = buildInstancesOutcome(instanceElements);
      executionSweepingOutputService.consume(
          ambiance, OutputExpressionConstants.INSTANCES, instancesOutcome, StepCategory.STAGE.name());
      Set<String> instances = getInstances(instanceElements);
      executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
          HostsOutput.builder().hosts(instances).build(), StepCategory.STAGE.name());
      return builder.stepOutcome(stepOutcome).build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  private Set<String> getInstances(List<CustomDeploymentServerInstanceInfo> instanceElements) {
    return instanceElements.stream()
        .map(CustomDeploymentServerInstanceInfo::getInstanceName)
        .collect(Collectors.toSet());
  }

  private InstancesOutcome buildInstancesOutcome(List<CustomDeploymentServerInstanceInfo> instanceElements) {
    List<InstanceOutcome> instanceOutcomeList = new ArrayList<>();
    for (CustomDeploymentServerInstanceInfo instance : instanceElements) {
      instanceOutcomeList.add(InstanceOutcome.builder()
                                  .hostName(instance.getInstanceName())
                                  .name(instance.getInstanceName())
                                  .host(HostOutcome.builder()
                                            .instanceName(instance.getInstanceName())
                                            .properties(instance.getProperties())
                                            .build())
                                  .build());
    }
    return InstancesOutcome.builder().instances(instanceOutcomeList).build();
  }

  private void closeLogStream(Ambiance ambiance) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
    }
  }

  private void validateInstanceElements(
      List<CustomDeploymentServerInstanceInfo> instanceElements, CustomDeploymentInfrastructureOutcome infrastructure) {
    final boolean elementWithoutHostnameExists = instanceElements.stream()
                                                     .map(CustomDeploymentServerInstanceInfo::getInstanceName)
                                                     .anyMatch(StringUtils::isBlank);
    if (elementWithoutHostnameExists) {
      throw new InvalidRequestException(
          format("Could not find \"%s\" field from Json Array",
              getHostnameFieldName(INSTANCE_NAME, infrastructure.getInstanceAttributes())),
          WingsException.USER);
    }
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
