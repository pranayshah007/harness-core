/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.resourceconstraint;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PMSStepPlanCreator;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceConstraintStepPlanCreator extends PMSStepPlanCreator {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.RESOURCE_CONSTRAINT);
  }

  @Override
  protected YamlField obtainNextSiblingField(YamlField currentField) {
    return currentField.getNode().nextSiblingNodeFromParentObject("execution");
  }
}
