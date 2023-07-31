/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameterV2;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.Report;
import io.harness.product.ci.engine.proto.RunStep;
import io.harness.product.ci.engine.proto.ShellType;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(CI)
public class RunStepProtobufSerializer implements ProtobufStepSerializer<RunStepInfo> {
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  public UnitStep serializeStepWithStepParameters(RunStepInfo runStepInfo, Integer port, String callbackId,
      String logKey, String identifier, ParameterField<Timeout> parameterFieldTimeout, String accountId,
      String stepName, Ambiance ambiance) {
    if (callbackId == null) {
      throw new CIStageExecutionException("CallbackId can not be null");
    }

    if (port == null) {
      throw new CIStageExecutionException("Port can not be null");
    }

    String gitSafeCMD =
        SerializerUtils.getSafeGitDirectoryCmd(RunTimeInputHandler.resolveShellType(runStepInfo.getShell()));

    String command = null;
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (ambiance.hasMetadata() && ambiance.getMetadata().getIsDebug()
        && featureFlagService.isEnabled(FeatureName.CI_REMOTE_DEBUG, accountId)) {
      command = SerializerUtils.getK8sDebugCommand(accountId, ciExecutionServiceConfig.getRemoteDebugTimeout(),
                    runStepInfo, ciExecutionServiceConfig.getTmateEndpoint())
          + System.lineSeparator()
          + RunTimeInputHandler.resolveStringParameter("Command", "Run", identifier, runStepInfo.getCommand(), true);
    } else {
      command =
          RunTimeInputHandler.resolveStringParameter("Command", "Run", identifier, runStepInfo.getCommand(), true);
    }

    RunStep.Builder runStepBuilder = RunStep.newBuilder();
    runStepBuilder.setCommand(gitSafeCMD + command);

    runStepBuilder.setContainerPort(port);
    Map<String, String> envvars =
        resolveMapParameterV2("envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    envvars = CIStepInfoUtils.injectAndResolveLoopingVariables(ambiance, accountId, featureFlagService, envvars);
    if (!isEmpty(envvars)) {
      runStepBuilder.putAllEnvironment(envvars);
    }

    UnitTestReport reports = runStepInfo.getReports().getValue();
    if (reports != null) {
      if (reports.getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) reports.getSpec();
        List<String> resolvedReport =
            RunTimeInputHandler.resolveListParameter("paths", "run", identifier, junitTestReport.getPaths(), false);

        Report report = Report.newBuilder().setType(Report.Type.JUNIT).addAllPaths(resolvedReport).build();
        runStepBuilder.addReports(report);
      }
    }

    if (isNotEmpty(runStepInfo.getOutputVariables().getValue())) {
      List<String> outputVarNames = runStepInfo.getOutputVariables()
                                        .getValue()
                                        .stream()
                                        .map(OutputNGVariable::getName)
                                        .collect(Collectors.toList());
      runStepBuilder.addAllEnvVarOutputs(outputVarNames);
    }

    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, runStepInfo.getDefaultTimeout());
    runStepBuilder.setContext(StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build());

    CIShellType shellType = RunTimeInputHandler.resolveShellType(runStepInfo.getShell());
    ShellType protoShellType = ShellType.SH;
    if (shellType == CIShellType.BASH) {
      protoShellType = ShellType.BASH;
    } else if (shellType == CIShellType.PWSH) {
      protoShellType = ShellType.PWSH;
    } else if (shellType == CIShellType.POWERSHELL) {
      protoShellType = ShellType.POWERSHELL;
    } else if (shellType == CIShellType.PYTHON) {
      protoShellType = ShellType.PYTHON;
    }

    runStepBuilder.setShellType(protoShellType);

    return UnitStep.newBuilder()
        .setAccountId(accountId)
        .setContainerPort(port)
        .setId(identifier)
        .setTaskId(callbackId)
        .setCallbackToken(delegateCallbackTokenSupplier.get().getToken())
        .setDisplayName(stepName)
        .setRun(runStepBuilder.build())
        .setLogKey(logKey)
        .build();
  }
}
