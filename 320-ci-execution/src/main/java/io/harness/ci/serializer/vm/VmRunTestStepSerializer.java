package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmRunTestStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunTestStep.VmRunTestStepBuilder;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VmRunTestStepSerializer {
  public VmRunTestStep serialize(RunTestsStepInfo runTestsStepInfo, String identifier,
      ParameterField<Timeout> parameterFieldTimeout, String stepName) {
    String buildTool = RunTimeInputHandler.resolveBuildTool(runTestsStepInfo.getBuildTool());
    if (buildTool == null) {
      throw new CIStageExecutionException("Build tool cannot be null");
    }
    String language = RunTimeInputHandler.resolveLanguage(runTestsStepInfo.getLanguage());
    if (language == null) {
      throw new CIStageExecutionException("language cannot be null");
    }
    List<String> outputVarNames = new ArrayList<>();
    if (isNotEmpty(runTestsStepInfo.getOutputVariables())) {
      outputVarNames =
          runTestsStepInfo.getOutputVariables().stream().map(OutputNGVariable::getName).collect(Collectors.toList());
    }

    String preCommand = RunTimeInputHandler.resolveStringParameter(
        "PreCommand", stepName, identifier, runTestsStepInfo.getPreCommand(), false);
    String postCommand = RunTimeInputHandler.resolveStringParameter(
        "PostCommand", stepName, identifier, runTestsStepInfo.getPostCommand(), false);
    String args =
        RunTimeInputHandler.resolveStringParameter("Command", stepName, identifier, runTestsStepInfo.getArgs(), true);
    String testAnnotations = RunTimeInputHandler.resolveStringParameter(
        "TestAnnotations", stepName, identifier, runTestsStepInfo.getTestAnnotations(), false);
    String packages = RunTimeInputHandler.resolveStringParameter(
        "Packages", stepName, identifier, runTestsStepInfo.getPackages(), false);

    boolean runOnlySelectedTests = resolveBooleanParameter(runTestsStepInfo.getRunOnlySelectedTests(), true);

    String image =
        RunTimeInputHandler.resolveStringParameter("Image", stepName, identifier, runTestsStepInfo.getImage(), false);
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, runTestsStepInfo.getDefaultTimeout());
    Map<String, String> envVars =
        resolveMapParameter("envVariables", stepName, identifier, runTestsStepInfo.getEnvVariables(), false);

    VmRunTestStepBuilder runTestStepBuilder =
        VmRunTestStep.builder()
            .image(image)
            .args(args)
            .entrypoint(SerializerUtils.getEntrypoint(runTestsStepInfo.getShell()))
            .language(language)
            .buildTool(buildTool)
            .packages(packages)
            .testAnnotations(testAnnotations)
            .runOnlySelectedTests(runOnlySelectedTests)
            .preCommand(preCommand)
            .postCommand(postCommand)
            .envVariables(envVars)
            .outputVariables(outputVarNames)
            .timeoutSecs(timeout);

    if (runTestsStepInfo.getReports() != null) {
      if (runTestsStepInfo.getReports().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) runTestsStepInfo.getReports().getSpec();
        List<String> resolvedReport = junitTestReport.resolve(identifier, stepName);

        runTestStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }

    return runTestStepBuilder.build();
  }
}
