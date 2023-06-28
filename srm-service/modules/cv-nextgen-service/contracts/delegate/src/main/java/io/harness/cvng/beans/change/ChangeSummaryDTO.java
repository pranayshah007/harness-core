/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChangeSummaryDTO {
  Map<ChangeCategory, CategoryCountDetails> categoryCountMap;
  CategoryCountDetails total;

  @Value
  @Builder
  public static class CategoryCountDetails {
    long count;
    long countInPrecedingWindow;
    double percentageChange;
  }
}
