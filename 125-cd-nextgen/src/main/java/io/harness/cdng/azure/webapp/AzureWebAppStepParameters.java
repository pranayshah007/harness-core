package io.harness.cdng.azure.webapp;

import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.List;

public interface AzureWebAppStepParameters {
  ParameterField<List<TaskSelectorYaml>> getDelegateSelectors();
}
