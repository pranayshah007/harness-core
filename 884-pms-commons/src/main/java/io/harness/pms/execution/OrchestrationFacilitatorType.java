/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class OrchestrationFacilitatorType {
  // Provided From the orchestration layer system facilitators
  public static final String SYNC = "SYNC";
  public static final String WAIT_STEP = "WAIT_STEP";
  public static final String ASYNC = "ASYNC";
  public static final String CHILD = "CHILD";
  public static final String CHILDREN = "CHILDREN";
  public static final String TASK = "TASK";
  public static final String TASK_CHAIN = "TASK_CHAIN";
  public static final String CHILD_CHAIN = "CHILD_CHAIN";
  public static final String ASYNC_CHAIN = "ASYNC_CHAIN";

  public static final List<String> ALL_FACILITATOR_TYPES =
      ImmutableList.of(SYNC, ASYNC, CHILD, CHILDREN, CHILD_CHAIN, TASK, TASK_CHAIN, WAIT_STEP, ASYNC_CHAIN);
}
