/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.concurrency;

import io.harness.pms.contracts.execution.Status;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConcurrentChildInstance {
  private List<String> childrenNodeExecutionIds;
  // the pointer to which node we should start from childrenNodeExecutionIds
  private int cursor;
  // This stores the status of children which will help us in knowing if we should continue or not.
  private List<Status> childStatuses;
}
