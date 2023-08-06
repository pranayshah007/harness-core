/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameterV2;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_MACHINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.Report;
import io.harness.product.ci.engine.proto.RunTestsStep;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CI)
public class RunTestsStepProtobufSerializer implements ProtobufStepSerializer<RunTestsStepInfo> {
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private SerializerUtils serializerUtils;

  public UnitStep serializeStepWithStepParameters(RunTestsStepInfo runTestsStepInfo, Integer port, String callbackId,
      String logKey, String identifier, ParameterField<Timeout> parameterFieldTimeout, String accountId,
      String stepName, Ambiance ambiance, String podName) {
    if (callbackId == null) {
      throw new CIStageExecutionException("CallbackId can not be null");
    }

    if (port == null) {
      throw new CIStageExecutionException("Port can not be null");
    }

    RunTestsStep.Builder runTestsStepBuilder = RunTestsStep.newBuilder();
    String gitSafeCMD = SerializerUtils.getSafeGitDirectoryCmd(
        RunTimeInputHandler.resolveShellType(runTestsStepInfo.getShell()), accountId, featureFlagService);
    String preTestCommand = RunTimeInputHandler.resolveStringParameter(
        "Command", "RunTests", identifier, runTestsStepInfo.getPreCommand(), false);
    if (EmptyPredicate.isNotEmpty(preTestCommand) && !preTestCommand.equals("null")) {
      preTestCommand = gitSafeCMD + preTestCommand;
    } else {
      preTestCommand = gitSafeCMD;
    }
    runTestsStepBuilder.setPreTestCommand(preTestCommand);
    String postTestCommand = RunTimeInputHandler.resolveStringParameter(
        "Command", "RunTests", identifier, runTestsStepInfo.getPostCommand(), false);
    if (StringUtils.isNotEmpty(postTestCommand)) {
      runTestsStepBuilder.setPostTestCommand(postTestCommand);
    }
    runTestsStepBuilder.setArgs(
        RunTimeInputHandler.resolveStringParameter("Args", "RunTests", identifier, runTestsStepInfo.getArgs(), true));
    runTestsStepBuilder.setContainerPort(port);

    String buildTool = RunTimeInputHandler.resolveBuildTool(runTestsStepInfo.getBuildTool());
    if (buildTool == null) {
      throw new CIStageExecutionException("Build tool cannot be null");
    }
    String language = RunTimeInputHandler.resolveLanguage(runTestsStepInfo.getLanguage());
    if (language == null) {
      throw new CIStageExecutionException("language cannot be null");
    }
    if (language.equalsIgnoreCase("python") && featureFlagService.isEnabled(FeatureName.CI_PYTHON_TI, accountId)) {
      throw new CIStageExecutionException("python TI is not enabled for this account");
    }
    runTestsStepBuilder.setLanguage(language.toLowerCase());
    runTestsStepBuilder.setBuildTool(buildTool.toLowerCase());

    runTestsStepBuilder.setRunOnlySelectedTests(
        resolveBooleanParameter(runTestsStepInfo.getRunOnlySelectedTests(), true));

    if (isNotEmpty(runTestsStepInfo.getOutputVariables().getValue())) {
      List<String> outputVarNames = runTestsStepInfo.getOutputVariables()
                                        .getValue()
                                        .stream()
                                        .map(OutputNGVariable::getName)
                                        .collect(Collectors.toList());
      runTestsStepBuilder.addAllEnvVarOutputs(outputVarNames);
    }

    Map<String, String> envvars =
        resolveMapParameterV2("envVariables", "RunTests", identifier, runTestsStepInfo.getEnvVariables(), false);
    envvars = CIStepInfoUtils.injectAndResolveLoopingVariables(ambiance, accountId, featureFlagService, envvars);
    if (!isEmpty(envvars)) {
      runTestsStepBuilder.putAllEnvironment(envvars);
    }
    envvars.put(DRONE_STAGE_MACHINE, podName);
    Map<String, String> statusEnvVars = serializerUtils.getStepStatusEnvVars(ambiance);
    envvars.putAll(statusEnvVars);

    String testAnnotations = RunTimeInputHandler.resolveStringParameter(
        "TestAnnotations", "RunTests", identifier, runTestsStepInfo.getTestAnnotations(), false);
    if (StringUtils.isNotEmpty(testAnnotations)) {
      runTestsStepBuilder.setTestAnnotations(testAnnotations);
    }

    String testRoot = RunTimeInputHandler.resolveStringParameter(
        "TestRoot", "RunTests", identifier, runTestsStepInfo.getTestRoot(), false);
    if (StringUtils.isNotEmpty(testRoot)) {
      runTestsStepBuilder.setTestRoot(testRoot);
    }

    String packages = RunTimeInputHandler.resolveStringParameter(
        "Packages", "RunTests", identifier, runTestsStepInfo.getPackages(), false);
    if (StringUtils.isNotEmpty(packages)) {
      runTestsStepBuilder.setPackages(packages);
    }

    String namespaces = RunTimeInputHandler.resolveStringParameter(
        "Namespaces", "RunTests", identifier, runTestsStepInfo.getNamespaces(), false);
    if (StringUtils.isNotEmpty(namespaces)) {
      runTestsStepBuilder.setNamespaces(namespaces);
    }

    String buildEnvName = RunTimeInputHandler.resolveDotNetBuildEnvName(runTestsStepInfo.getBuildEnvironment());
    if (StringUtils.isNotEmpty(buildEnvName)) {
      runTestsStepBuilder.setBuildEnvironment(buildEnvName.toLowerCase());
    }

    String frameworkVersion = RunTimeInputHandler.resolveDotNetVersion(runTestsStepInfo.getFrameworkVersion());
    if (StringUtils.isNotEmpty(frameworkVersion)) {
      runTestsStepBuilder.setFrameworkVersion(frameworkVersion.toLowerCase());
    }

    String pythonVersion = RunTimeInputHandler.resolvePythonVersion(runTestsStepInfo.getPythonVersion());
    if (StringUtils.isNotEmpty(pythonVersion)) {
      runTestsStepBuilder.setPythonVersion(pythonVersion.toLowerCase());
    }

    UnitTestReport reports = runTestsStepInfo.getReports().getValue();
    if (reports != null) {
      if (reports.getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) reports.getSpec();
        List<String> resolvedReport = RunTimeInputHandler.resolveListParameter(
            "paths", "runtests", identifier, junitTestReport.getPaths(), false);
        Report report = Report.newBuilder().setType(Report.Type.JUNIT).addAllPaths(resolvedReport).build();
        runTestsStepBuilder.addReports(report);
      }
    }
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, runTestsStepInfo.getDefaultTimeout());

    runTestsStepBuilder.setParallelizeTests(resolveBooleanParameter(runTestsStepInfo.getEnableTestSplitting(), false));
    String testSplitStrategy = RunTimeInputHandler.resolveSplitStrategy(runTestsStepInfo.getTestSplitStrategy());
    if (StringUtils.isNotEmpty(testSplitStrategy)) {
      runTestsStepBuilder.setTestSplitStrategy(SerializerUtils.getTestSplitStrategy(testSplitStrategy));
    }
    String testGlobs = RunTimeInputHandler.resolveStringParameter(
        "testGlobs", stepName, identifier, runTestsStepInfo.getTestGlobs(), false);
    if (StringUtils.isNotEmpty(testGlobs)) {
      runTestsStepBuilder.setTestGlobs(testGlobs);
    }

    runTestsStepBuilder.setContext(StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build());

    return UnitStep.newBuilder()
        .setId(identifier)
        .setAccountId(accountId)
        .setContainerPort(port)
        .setTaskId(callbackId)
        .setCallbackToken(delegateCallbackTokenSupplier.get().getToken())
        .setDisplayName(stepName)
        .setRunTests(runTestsStepBuilder.build())
        .setLogKey(logKey)
        .build();
  }
}
