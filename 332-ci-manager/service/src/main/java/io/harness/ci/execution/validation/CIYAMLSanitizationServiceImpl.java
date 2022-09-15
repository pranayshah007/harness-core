package io.harness.ci.validation;

import static io.harness.beans.steps.CIStepInfoType.RUN;
import static io.harness.beans.steps.CIStepInfoType.RUN_TESTS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIYAMLSanitizationServiceImpl implements CIYAMLSanitizationService {
  Set<String> maliciousMiningPatterns = new HashSet<>();
  static final String MALICIOUS_PATTERNS_FILE = "suspiciousMiningPatterns.txt";

  @Inject
  public CIYAMLSanitizationServiceImpl() {
    if (maliciousMiningPatterns.isEmpty()) {
      try (InputStream inputStream =
               Thread.currentThread().getContextClassLoader().getResourceAsStream(MALICIOUS_PATTERNS_FILE);
           BufferedReader bufferedReader =
               new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String line = bufferedReader.readLine();
        while (isNotEmpty(line)) {
          maliciousMiningPatterns.add(line.trim());
          line = bufferedReader.readLine();
        }
      } catch (Exception e) {
        log.error("Failed to read malicious patterns from file.", e);
      }
    }
  }

  @Override
  public boolean validate(List<ExecutionWrapperConfig> wrapper) {
    for (ExecutionWrapperConfig executionWrapper : wrapper) {
      if (executionWrapper.getStep() == null || executionWrapper.getStep().isNull()) {
        continue;
      }
      CIAbstractStepNode abstractNode = IntegrationStageUtils.getStepNode(executionWrapper);
      if (!(abstractNode.getStepSpecType() instanceof CIStepInfo)) {
        continue;
      }

      CIStepInfo ciStepInfo = (CIStepInfo) abstractNode.getStepSpecType();

      String command = null;

      if (ciStepInfo.getNonYamlInfo().getStepInfoType() == RUN) {
        ParameterField<String> commandParam = ((RunStepInfo) ciStepInfo).getCommand();
        if (defaultCommand(commandParam)) {
          continue;
        }
        command = commandParam.getValue();
      } else if (ciStepInfo.getNonYamlInfo().getStepInfoType() == RUN_TESTS) {
        ParameterField<String> preCommand = ((RunTestsStepInfo) ciStepInfo).getPreCommand();
        ParameterField<String> postCommand = ((RunTestsStepInfo) ciStepInfo).getPostCommand();
        if (defaultCommand(preCommand) && defaultCommand(postCommand)) {
          continue;
        }
        command = preCommand.getValue() + " " + postCommand.getValue();
      }

      if (command == null) {
        continue;
      }

      for (String maliciousKeyword : maliciousMiningPatterns) {
        if (command.contains(maliciousKeyword)) {
          log.error("Malicious keyword: \"{}\", detected in command: \"{}\"", maliciousKeyword, command);
          throw new CIStageExecutionException("Invalid step - Malicious activity detected");
        }
      }
    }
    return true;
  }

  private boolean defaultCommand(ParameterField<String> command) {
    if (command.getValue().equals(command.getDefaultValue())) {
      return true;
    }
    return false;
  }
}
