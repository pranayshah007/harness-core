package io.harness.plancreator.steps.email;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.EMAIL)
@TypeAlias("EmailStepNode")
@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.email.EmailStepNode")
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
