/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_FREEZE})
@Data
@Builder
public class FreezeDuration {
  private static final String DURATION_STRING = "duration";
  private static final String SPACE = " ";
  private static final String ERROR_HELPER = "Valid duration contain units m/h/d/w and are written like 10m or 1h 10m";

  @Getter
  public enum TimeUnit {
    SECONDS("s", 1000),
    MINUTES("m", 60 * SECONDS.coefficient),
    HOURS("h", 60 * MINUTES.coefficient),
    DAYS("d", 24 * HOURS.coefficient),
    WEEKS("w", 7 * DAYS.coefficient);

    String suffix;
    Integer coefficient; // in relation to milliseconds

    TimeUnit(String unit, Integer coefficient) {
      this.suffix = unit;
      this.coefficient = coefficient;
    }

    public static Optional<FreezeDuration.TimeUnit> findByUnit(String unit) {
      return Stream.of(FreezeDuration.TimeUnit.values()).filter(timeUnit -> timeUnit.suffix.equals(unit)).findFirst();
    }
  }

  private String timeoutString;
  private long timeoutInMillis;

  @JsonCreator
  public static FreezeDuration fromString(String timeout) {
    try {
      if (isEmpty(timeout)) {
        return null;
      }

      long totalValue = 0;
      long currentValue = 0;
      String prevValidSymbol = "";
      for (char ch : timeout.toCharArray()) {
        String currentSymbol = String.valueOf(ch);
        if (isDigit(currentSymbol)) {
          currentValue = (currentValue * 10) + Long.parseLong(currentSymbol);
        } else if (isUnitCharacter(currentSymbol) && isDigit(prevValidSymbol)) {
          currentValue = currentValue * getMultiplyCoefficient(currentSymbol, timeout);
          totalValue += currentValue;
          currentValue = 0;
        } else if (!(currentSymbol.equals(SPACE) && isUnitCharacter(prevValidSymbol))) {
          // Condition added to allow strings of style "1d 2h", via this statement we ignore space after s/m/h/d/w
          throw new InvalidArgumentsException(Pair.of(DURATION_STRING, timeout), ERROR_HELPER);
        }

        prevValidSymbol = currentSymbol;
      }

      if (currentValue != 0 || !isUnitCharacter(prevValidSymbol)) {
        throw new InvalidArgumentsException(Pair.of(DURATION_STRING, timeout), ERROR_HELPER);
      }

      return FreezeDuration.builder().timeoutInMillis(totalValue).timeoutString(timeout).build();

    } catch (NumberFormatException e) {
      throw new InvalidArgumentsException(Pair.of(DURATION_STRING, timeout), e);
    }
  }

  private static boolean isDigit(String symbol) {
    return isNotEmpty(symbol) && Character.isDigit(symbol.charAt(0));
  }

  private static boolean isUnitCharacter(String symbol) {
    return FreezeDuration.TimeUnit.findByUnit(symbol).isPresent();
  }

  private static long getMultiplyCoefficient(String symbol, String timeout) {
    return FreezeDuration.TimeUnit.findByUnit(symbol)
        .orElseThrow(() -> new InvalidArgumentsException(Pair.of(DURATION_STRING, timeout)))
        .coefficient;
  }
}
