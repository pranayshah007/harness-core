/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * This represents node type in a plan
 * A node can be of the type
 *  PLAN -- Complete plan is a node
 *  PLAN_NODE -- A sigle node
 *
 *  More node types can be introduced and on the basis of that execution engine can make certain execution choices
 *  RESUMEABLE_PLAN_NODE
 *
 */
@OwnedBy(HarnessTeam.PIPELINE) public enum NodeType { PLAN, PLAN_NODE, IDENTITY_PLAN_NODE }
