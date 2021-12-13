package io.harness.cvng.cdng.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import java.util.List;
import lombok.Data;

@OwnedBy(HarnessTeam.CV)
@Data
public abstract class CVNGAbstractStepNode extends AbstractStepNode {
  // check if this field is needed or not
  List<FailureStrategyConfig> failureStrategies;
}
