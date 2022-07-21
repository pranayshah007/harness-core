/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plan.NodeType;

/**
 * Different node types can have different execution models.
 * For plan -> PlanExecution
 * For node -> NodeExecution
 *
 * We can introduce event node. This is a marker interface for such entities
 *
 * P.S. Would have ideally liked it to be called NodeExecution but as it is already present adding PMS to it
 */

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsNodeExecution {
  String getNodeId();
  NodeType getNodeType();
}
