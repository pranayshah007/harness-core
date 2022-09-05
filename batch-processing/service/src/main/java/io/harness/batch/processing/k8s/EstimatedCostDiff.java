/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import javax.annotation.Nonnull;
import lombok.Value;

@Value
public class EstimatedCostDiff {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  @Nonnull BigDecimal oldCost;
  @Nonnull BigDecimal newCost;

  public BigDecimal getDiffAmount() {
    return newCost.subtract(oldCost);
  }

  public BigDecimal getDiffAmountPercent() {
    return ZERO.compareTo(oldCost) == 0
        ? null
        : getDiffAmount().multiply(BigDecimal.valueOf(100)).divide(oldCost, 2, ROUND_DOWN);
  }
}
