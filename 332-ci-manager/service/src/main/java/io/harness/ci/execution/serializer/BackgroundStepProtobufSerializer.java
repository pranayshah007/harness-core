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

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.product.ci.engine.proto.Report;
import io.harness.product.ci.engine.proto.RunStep;
import io.harness.product.ci.engine.proto.ShellType;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
@OwnedBy(CI)
public class BackgroundStepProtobufSerializer implements ProtobufStepSerializer<BackgroundStepInfo> {
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private CIFeatureFlagService featureFlagService;

  public UnitStep serializeStepWithStepParameters(BackgroundStepInfo backgroundStepInfo, Integer port,
      String callbackId, String logKey, String identifier, String accountId, String stepName, Ambiance ambiance) {
    if (callbackId == null) {
      throw new CIStageExecutionException("CallbackId can not be null");
    }

    if (port == null) {
      throw new CIStageExecutionException("Port can not be null");
    }

    RunStep.Builder runStepBuilder = RunStep.newBuilder();
    runStepBuilder.setCommand(Optional
                                  .ofNullable(RunTimeInputHandler.resolveStringParameter(
                                      "Command", "Background", identifier, backgroundStepInfo.getCommand(), false))
                                  .orElse(""));

    runStepBuilder.setContainerPort(port);

    Map<String, String> envVars =
        resolveMapParameterV2("envVariables", "Background", identifier, backgroundStepInfo.getEnvVariables(), false);
    envVars = CIStepInfoUtils.injectAndResolveLoopingVariables(ambiance, accountId, featureFlagService, envVars);
    if (!isEmpty(envVars)) {
      runStepBuilder.putAllEnvironment(envVars);
    }

    UnitTestReport reports = backgroundStepInfo.getReports().getValue();
    if (reports != null) {
      if (reports.getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) reports.getSpec();
        List<String> resolvedReport = RunTimeInputHandler.resolveListParameter(
            "paths", "background", identifier, junitTestReport.getPaths(), false);

        Report report = Report.newBuilder().setType(Report.Type.JUNIT).addAllPaths(resolvedReport).build();
        runStepBuilder.addReports(report);
      }
    }

    runStepBuilder.setContext(StepContext.newBuilder().build());

    CIShellType shellType = RunTimeInputHandler.resolveShellType(backgroundStepInfo.getShell());
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
    runStepBuilder.setDetach(true);
    runStepBuilder.addAllEntrypoint(Optional
                                        .ofNullable(RunTimeInputHandler.resolveListParameter("entrypoint", "background",
                                            identifier, backgroundStepInfo.getEntrypoint(), false))
                                        .orElse(emptyList()));
    runStepBuilder.setImage(RunTimeInputHandler.resolveStringParameter(
        "Image", "background", identifier, backgroundStepInfo.getImage(), true));

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
