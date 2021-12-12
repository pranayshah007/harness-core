package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep.VmRunStepBuilder;
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
public class VmRunStepSerializer {
  public VmRunStep serialize(
      RunStepInfo runStepInfo, String identifier, ParameterField<Timeout> parameterFieldTimeout, String stepName) {
    String command =
        RunTimeInputHandler.resolveStringParameter("Command", "Run", identifier, runStepInfo.getCommand(), true);
    String image =
        RunTimeInputHandler.resolveStringParameter("Image", "Run", identifier, runStepInfo.getImage(), false);
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, runStepInfo.getDefaultTimeout());
    Map<String, String> envVars =
        resolveMapParameter("envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);

    List<String> outputVarNames = new ArrayList<>();
    if (isNotEmpty(runStepInfo.getOutputVariables())) {
      outputVarNames =
          runStepInfo.getOutputVariables().stream().map(OutputNGVariable::getName).collect(Collectors.toList());
    }

    VmRunStepBuilder runStepBuilder = VmRunStep.builder()
                                          .image(image)
                                          .entrypoint(SerializerUtils.getEntrypoint(runStepInfo.getShell()))
                                          .command(command)
                                          .outputVariables(outputVarNames)
                                          .envVariables(envVars)
                                          .timeoutSecs(timeout);

    if (runStepInfo.getReports() != null) {
      if (runStepInfo.getReports().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) runStepInfo.getReports().getSpec();
        List<String> resolvedReport = junitTestReport.resolve(identifier, "run");

        runStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }

    return runStepBuilder.build();
  }
}
