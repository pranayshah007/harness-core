/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitx;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContextData;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class ThreadOperationContext implements GlobalContextData {
  public static final String THREAD_OPERATION_CONTEXT = "THREAD_OPERATION_CONTEXT";
  USER_FLOW userFlow;

  @Override
  public String getKey() {
    return THREAD_OPERATION_CONTEXT;
  }
}
