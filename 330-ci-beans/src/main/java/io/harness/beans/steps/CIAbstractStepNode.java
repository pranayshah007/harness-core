package io.harness.beans.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.AbstractStepNode;

import lombok.Data;

@OwnedBy(HarnessTeam.CI)
@Data
public abstract class CIAbstractStepNode extends AbstractStepNode {}
