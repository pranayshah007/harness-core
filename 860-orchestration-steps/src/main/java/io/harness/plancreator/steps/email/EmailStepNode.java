package io.harness.plancreator.steps.email;

import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EmailStepNode extends PmsAbstractStepNode {
  EmailStepInfo emailStepInfo;
  @Override
  public String getType() {
    return StepSpecTypeConstants.EMAIL;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return emailStepInfo;
  }

  //    Not return enum type like httpstep
}
