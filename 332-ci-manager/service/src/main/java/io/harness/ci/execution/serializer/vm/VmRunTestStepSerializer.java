/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameterV2;
import static io.harness.ci.commonconstants.CIExecutionConstants.NULL_STR;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmRunTestStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunTestStep.VmRunTestStepBuilder;
import io.harness.exception.ngexception.CIStageExecutionException;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class VmRunTestStepSerializer {
  @Inject ConnectorUtils connectorUtils;
  @Inject CIStepInfoUtils ciStepInfoUtils;
  String NULL_STR = "null";

  public VmRunTestStep serialize(RunTestsStepInfo runTestsStepInfo, String identifier,
      ParameterField<Timeout> parameterFieldTimeout, String stepName, Ambiance ambiance, List<CIRegistry> registries) {
    String buildTool = RunTimeInputHandler.resolveBuildTool(runTestsStepInfo.getBuildTool());
    if (buildTool == null) {
      throw new CIStageExecutionException("Build tool cannot be null");
    }
    String language = RunTimeInputHandler.resolveLanguage(runTestsStepInfo.getLanguage());
    if (language == null) {
      throw new CIStageExecutionException("language cannot be null");
    }
    List<String> outputVarNames = new ArrayList<>();
    if (isNotEmpty(runTestsStepInfo.getOutputVariables().getValue())) {
      outputVarNames = runTestsStepInfo.getOutputVariables()
                           .getValue()
                           .stream()
                           .map(OutputNGVariable::getName)
                           .collect(Collectors.toList());
    }
    String image =
        RunTimeInputHandler.resolveStringParameter("Image", stepName, identifier, runTestsStepInfo.getImage(), false);
    if (isNotEmpty(image) && image.equals(NULL_STR)) {
      image = "";
    }
    String connectorIdentifier;

    if (isNotEmpty(registries)) {
      connectorIdentifier = ciStepInfoUtils.resolveConnectorFromRegistries(registries, image).orElse(null);
    } else {
      connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
          "connectorRef", "RunTest", identifier, runTestsStepInfo.getConnectorRef(), false);
    }

    String preCommand = RunTimeInputHandler.resolveStringParameter(
        "PreCommand", stepName, identifier, runTestsStepInfo.getPreCommand(), false);
    if (preCommand == null || preCommand.equals(NULL_STR)) {
      preCommand = "";
    }

    String postCommand = RunTimeInputHandler.resolveStringParameter(
        "PostCommand", stepName, identifier, runTestsStepInfo.getPostCommand(), false);
    String args =
        RunTimeInputHandler.resolveStringParameter("Command", stepName, identifier, runTestsStepInfo.getArgs(), true);
    String testAnnotations = RunTimeInputHandler.resolveStringParameter(
        "TestAnnotations", stepName, identifier, runTestsStepInfo.getTestAnnotations(), false);
    String testRoot = RunTimeInputHandler.resolveStringParameter(
        "TestRoot", stepName, identifier, runTestsStepInfo.getTestRoot(), false);
    String packages = RunTimeInputHandler.resolveStringParameter(
        "Packages", stepName, identifier, runTestsStepInfo.getPackages(), false);
    String namespaces = RunTimeInputHandler.resolveStringParameter(
        "Namespaces", stepName, identifier, runTestsStepInfo.getNamespaces(), false);
    String buildEnvironment = RunTimeInputHandler.resolveDotNetBuildEnvName(runTestsStepInfo.getBuildEnvironment());
    String frameworkVersion = RunTimeInputHandler.resolveDotNetVersion(runTestsStepInfo.getFrameworkVersion());
    String pythonVersion = RunTimeInputHandler.resolvePythonVersion(runTestsStepInfo.getPythonVersion());

    boolean runOnlySelectedTests = resolveBooleanParameter(runTestsStepInfo.getRunOnlySelectedTests(), true);
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, runTestsStepInfo.getDefaultTimeout());
    Map<String, String> envVars =
        resolveMapParameterV2("envVariables", stepName, identifier, runTestsStepInfo.getEnvVariables(), false);

    String earlyExitCommand = SerializerUtils.getEarlyExitCommand(runTestsStepInfo.getShell());
    preCommand = earlyExitCommand + preCommand;

    VmRunTestStepBuilder runTestStepBuilder =
        VmRunTestStep.builder()
            .image(image)
            .args(args)
            .entrypoint(SerializerUtils.getEntrypoint(runTestsStepInfo.getShell()))
            .language(language)
            .buildTool(buildTool)
            .packages(packages)
            .namespaces(namespaces)
            .testAnnotations(testAnnotations)
            .runOnlySelectedTests(runOnlySelectedTests)
            .preCommand(preCommand)
            .postCommand(postCommand)
            .envVariables(envVars)
            .outputVariables(outputVarNames)
            .timeoutSecs(timeout)
            .buildEnvironment(buildEnvironment)
            .frameworkVersion(frameworkVersion)
            .pythonVersion(pythonVersion)
            .testRoot(testRoot);

    ConnectorDetails connectorDetails;
    if (!StringUtils.isEmpty(image) && !StringUtils.isEmpty(connectorIdentifier)) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
      runTestStepBuilder.connector(connectorDetails);

      runTestStepBuilder.privileged(
          RunTimeInputHandler.resolveBooleanParameter(runTestsStepInfo.getPrivileged(), false));
      if (runTestsStepInfo.getRunAsUser() != null && runTestsStepInfo.getRunAsUser().getValue() != null) {
        runTestStepBuilder.runAsUser(runTestsStepInfo.getRunAsUser().getValue().toString());
      }
    }

    runTestStepBuilder.parallelizeTests(resolveBooleanParameter(runTestsStepInfo.getEnableTestSplitting(), false));
    String testSplitStrategy = RunTimeInputHandler.resolveSplitStrategy(runTestsStepInfo.getTestSplitStrategy());
    if (StringUtils.isNotEmpty(testSplitStrategy)) {
      runTestStepBuilder.testSplitStrategy(SerializerUtils.getTestSplitStrategy(testSplitStrategy));
    }
    runTestStepBuilder.testGlobs(RunTimeInputHandler.resolveStringParameter(
        "testGlobs", stepName, identifier, runTestsStepInfo.getTestGlobs(), false));

    if (runTestsStepInfo.getReports().getValue() != null) {
      if (runTestsStepInfo.getReports().getValue().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) runTestsStepInfo.getReports().getValue().getSpec();
        List<String> resolvedReport =
            RunTimeInputHandler.resolveListParameter("paths", stepName, identifier, junitTestReport.getPaths(), false);
        runTestStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }

    return runTestStepBuilder.build();
  }
}
