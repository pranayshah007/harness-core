/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.skip.skipper.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.GraphVertex;
import io.harness.skip.skipper.VertexSkipper;

@OwnedBy(CDC)
public class NoOpSkipper extends VertexSkipper {
  @Override
  public void skip(EphemeralOrchestrationGraph orchestrationGraph, GraphVertex skippedVertex) {
    // no op
  }
}
