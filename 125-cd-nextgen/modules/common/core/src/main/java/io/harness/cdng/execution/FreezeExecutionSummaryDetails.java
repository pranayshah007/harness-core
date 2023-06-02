/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FreezeExecutionSummaryDetails {
  List<FreezeExecutionInfoDetails> freezeExecutionInfoList;

  @Value
  @Builder
  public static class FreezeExecutionInfoDetails {
    String identifier;
    String orgIdentifier;
    String projectIdentifier;
    String yaml;
    String name;
    String freezeType;
  }
}
