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

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityNodeExecutionMetadata implements PmsNodeExecutionMetadata {
  @Override
  public NodeType forNodeType() {
    return NodeType.IDENTITY_PLAN_NODE;
  }
}
