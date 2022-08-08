/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.quantity.unit;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Getter
@OwnedBy(CI)
@TypeAlias("decimalQuantityUnit")
@RecasterAlias("io.harness.beans.quantity.unit.DecimalQuantityUnit")
public enum DecimalQuantityUnit {
  m(10, -3, "m"),
  unitless(10, 0, "");

  private final long base;
  private final long exponent;
  private final String suffix;

  DecimalQuantityUnit(long base, long exponent, String suffix) {
    this.base = base;
    this.exponent = exponent;
    this.suffix = suffix;
  }
}
