/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.state.inspection;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@OwnedBy(CDC)
public interface StateInspectionService {
  StateInspection get(String stateExecutionInstanceId);
  void search(Set<String> stateExecutionInstanceIds, Consumer<StateInspection> consumer);
  void append(String stateExecutionInstanceId, StateInspectionData data);
  void append(String stateExecutionInstanceId, List<StateInspectionData> data);
}
