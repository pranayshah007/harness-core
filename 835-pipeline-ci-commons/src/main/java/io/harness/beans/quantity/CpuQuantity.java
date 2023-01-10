/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.quantity;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(CI)
@TypeAlias("cpuQuantity")
@RecasterAlias("io.harness.beans.quantity.CpuQuantity")
public class CpuQuantity {
  private static final String PARTS_REGEX = "[m]";

  private String numericValue;
  private DecimalQuantityUnit unit;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static CpuQuantity fromString(String quantity) {
    try {
      if (isEmpty(quantity)) {
        return null;
      }
      String[] parts = quantity.split(PARTS_REGEX);
      String numericValue = parts[0];
      String suffix = quantity.substring(parts[0].length());
      DecimalQuantityUnit unit = Stream.of(DecimalQuantityUnit.values())
                                     .filter(quantityUnit -> quantityUnit.getSuffix().equals(suffix))
                                     .findFirst()
                                     .orElseThrow(() -> new InvalidArgumentsException(Pair.of("cpu", quantity)));

      return CpuQuantity.builder().numericValue(numericValue).unit(unit).build();
    } catch (NumberFormatException e) {
      throw new InvalidArgumentsException(Pair.of("cpu", quantity), e);
    }
  }

  @JsonValue
  public String getYamlProperty() {
    return numericValue + unit.getSuffix();
  }
}
