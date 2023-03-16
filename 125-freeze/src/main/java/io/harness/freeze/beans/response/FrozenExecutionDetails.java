/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@EqualsAndHashCode
public class FrozenExecutionDetails {
  List<FrozenExecutionDetail> freezeList;

  @Value
  @Builder
  @EqualsAndHashCode
  public static class FrozenExecutionDetail {
    FreezeSummaryResponseDTO freeze;
    String url;
  }
}
