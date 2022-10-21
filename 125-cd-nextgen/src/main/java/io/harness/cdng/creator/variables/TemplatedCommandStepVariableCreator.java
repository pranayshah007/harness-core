/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ssh.CommandStepNode;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TemplatedCommandStepVariableCreator extends GenericStepVariableCreator<CommandStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(PlanCreatorUtils.TEMPLATE_TYPE);
  }

  @Override
  public Class<CommandStepNode> getFieldClass() {
    return CommandStepNode.class;
  }
}
