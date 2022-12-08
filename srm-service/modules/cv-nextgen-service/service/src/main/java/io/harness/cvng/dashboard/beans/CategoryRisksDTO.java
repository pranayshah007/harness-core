/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.beans;

import io.harness.cvng.beans.CVMonitoringCategory;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Value
@Builder
public class CategoryRisksDTO {
  long startTimeEpoch;
  long endTimeEpoch;

  List<CategoryRisk> categoryRisks;

  @Builder.Default boolean hasConfigsSetup = true;

  @Data
  @Builder
  public static class CategoryRisk {
    CVMonitoringCategory category;
    Integer risk;
  }
}
