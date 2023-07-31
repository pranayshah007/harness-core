/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static software.wings.beans.TaskType.SHELL_SCRIPT_PROVISION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.ssh.SshCommandStepHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNG;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNGRequest;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNGResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ShellScriptProvisionStep extends CdTaskExecutable<ShellScriptProvisionTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SHELL_SCRIPT_PROVISION.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private StepHelper stepHelper;
  @Inject private SshCommandStepHelper sshCommandStepHelper;
  @Inject private ProvisionerOutputHelper provisionerOutputHelper;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    ShellScriptProvisionStepParameters stepParameters =
        (ShellScriptProvisionStepParameters) stepElementParameters.getSpec();
    log.info("Starting execution Obtain Task after Rbac for Shell Script Provision step");

    String scriptBody = sshCommandStepHelper.getShellScript(ambiance, stepParameters.getSource());
    if (isEmpty(scriptBody)) {
      throw new InvalidRequestException("Script cannot be empty or null");
    }

    ShellScriptProvisionTaskNGRequest taskParameters =
        ShellScriptProvisionTaskNGRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .executionId(String.format("%s-%s-%s", ambiance.getPlanExecutionId(), ambiance.getStageExecutionId(),
                stepElementParameters.getIdentifier()))
            .scriptBody(scriptBody)
            .variables(getEnvironmentVariables(stepParameters.getEnvironmentVariables()))
            .timeoutInMillis(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(SHELL_SCRIPT_PROVISION.name())
                            .parameters(new Object[] {taskParameters})
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(ShellScriptProvisionTaskNG.COMMAND_UNIT), SHELL_SCRIPT_PROVISION.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(
            ParameterFieldHelper.getParameterFieldValue(stepParameters.getDelegateSelectors())),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters,
      ThrowingSupplier<ShellScriptProvisionTaskNGResponse> responseSupplier) throws Exception {
    log.info("Handling Task Result With Security Context for Shell Script Provision step");
    ShellScriptProvisionTaskNGResponse response = responseSupplier.get();

    if (response.getCommandExecutionStatus().equals(FAILURE)) {
      return StepResponse.builder()
          .unitProgressList(response.getUnitProgressData().getUnitProgresses())
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
          .build();
    }

    ShellScriptProvisionOutcome shellScriptProvisionOutcome =
        new ShellScriptProvisionOutcome(parseOutput(response.getOutput()));
    provisionerOutputHelper.saveProvisionerOutputByStepIdentifier(ambiance, shellScriptProvisionOutcome);
    return StepResponse.builder()
        .unitProgressList(response.getUnitProgressData().getUnitProgresses())
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(shellScriptProvisionOutcome)
                         .build())
        .status(Status.SUCCEEDED)
        .build();
  }

  private Map<String, String> getEnvironmentVariables(Map<String, Object> inputVariables) {
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(String.format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.warn(String.format(
            "Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }

  private Map<String, Object> parseOutput(String output) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isEmpty(output)) {
      return outputs;
    }

    TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
    try {
      return new ObjectMapper().readValue(output, typeRef);
    } catch (IOException e) {
      log.error("Filed to parse output : " + e.getMessage());
      throw new InvalidRequestException(String.format("Not a json type output: %s", e.getMessage()));
    }
  }
}