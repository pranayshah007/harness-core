package io.harness.steps.Email;

import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;

public class EmailStepParameters extends EmailBaseStepInfo implements SpecParameters {
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Builder(builderMethodName = "infoBuilder")
  public EmailStepParameters(ParameterField<List<String>> To_Mail_IDs, ParameterField<List<String>> CC_Mail_IDs,
      ParameterField<String> subject, ParameterField<String> body,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(To_Mail_IDs, CC_Mail_IDs, subject, body);
    this.delegateSelectors = delegateSelectors;
  }
}
