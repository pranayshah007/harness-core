/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameterV2;
import static io.harness.ci.commonconstants.CIExecutionConstants.NULL_STR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.FeatureName;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep.VmRunStepBuilder;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class VmRunStepSerializer {
  @Inject CIStepInfoUtils ciStepInfoUtils;
  @Inject ConnectorUtils connectorUtils;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private SerializerUtils serializerUtils;

  public VmRunStep serialize(RunStepInfo runStepInfo, Ambiance ambiance, String identifier,
      ParameterField<Timeout> parameterFieldTimeout, String stepName, List<CIRegistry> registries, String delegateId,
      StageInfraDetails stageInfraDetails) {
    String command =
        RunTimeInputHandler.resolveStringParameter("Command", "Run", identifier, runStepInfo.getCommand(), true);
    String image =
        RunTimeInputHandler.resolveStringParameter("Image", "Run", identifier, runStepInfo.getImage(), false);
    String connectorIdentifier;

    if (isNotEmpty(image) && image.equals(NULL_STR)) {
      image = "";
    }

    if (isNotEmpty(registries)) {
      connectorIdentifier = ciStepInfoUtils.resolveConnectorFromRegistries(registries, image).orElse(null);
    } else {
      connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
          "connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), false);
    }

    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, runStepInfo.getDefaultTimeout());

    Map<String, String> envVars =
        resolveMapParameterV2("envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    envVars = CIStepInfoUtils.injectAndResolveLoopingVariables(
        ambiance, AmbianceUtils.getAccountId(ambiance), featureFlagService, envVars);

    if (StringUtils.isNotEmpty(delegateId)) {
      if (isEmpty(envVars)) {
        envVars = new HashMap<>();
      }
      envVars.put("HARNESS_DELEGATE_ID", delegateId);
    }

    Map<String, String> statusEnvVars = serializerUtils.getStepStatusEnvVars(ambiance);
    envVars.putAll(statusEnvVars);

    List<String> outputVarNames = new ArrayList<>();
    if (isNotEmpty(runStepInfo.getOutputVariables().getValue())) {
      outputVarNames = runStepInfo.getOutputVariables()
                           .getValue()
                           .stream()
                           .map(OutputNGVariable::getName)
                           .collect(Collectors.toList());
    }

    String earlyExitCommand = SerializerUtils.getEarlyExitCommand(runStepInfo.getShell());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (ambiance.hasMetadata() && ambiance.getMetadata().getIsDebug()
        && featureFlagService.isEnabled(FeatureName.CI_REMOTE_DEBUG, ngAccess.getAccountIdentifier())) {
      command = earlyExitCommand + System.lineSeparator()
          + SerializerUtils.getVmDebugCommand(ngAccess.getAccountIdentifier(),
              ciExecutionServiceConfig.getRemoteDebugTimeout(), runStepInfo, stageInfraDetails,
              envVars.get("TMATE_PATH"), ciExecutionServiceConfig.getTmateEndpoint())
          + System.lineSeparator() + command;
    } else {
      command = earlyExitCommand + command;
    }

    VmRunStepBuilder runStepBuilder = VmRunStep.builder()
                                          .image(image)
                                          .entrypoint(SerializerUtils.getEntrypoint(runStepInfo.getShell()))
                                          .command(command)
                                          .outputVariables(outputVarNames)
                                          .envVariables(envVars)
                                          .timeoutSecs(timeout);

    ConnectorDetails connectorDetails;
    if (!StringUtils.isEmpty(image) && !StringUtils.isEmpty(connectorIdentifier)) {
      ngAccess = AmbianceUtils.getNgAccess(ambiance);
      connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
      runStepBuilder.imageConnector(connectorDetails);
      runStepBuilder.privileged(RunTimeInputHandler.resolveBooleanParameter(runStepInfo.getPrivileged(), false));
      if (runStepInfo.getRunAsUser() != null && runStepInfo.getRunAsUser().getValue() != null) {
        runStepBuilder.runAsUser(runStepInfo.getRunAsUser().getValue().toString());
      }
    }

    if (runStepInfo.getReports().getValue() != null) {
      if (runStepInfo.getReports().getValue().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) runStepInfo.getReports().getValue().getSpec();
        List<String> resolvedReport =
            RunTimeInputHandler.resolveListParameter("paths", "run", identifier, junitTestReport.getPaths(), false);
        runStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }

    return runStepBuilder.build();
  }
}
