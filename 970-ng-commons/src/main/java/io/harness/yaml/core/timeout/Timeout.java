/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.timeout;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.yaml.core.timeout.Timeout")
public class Timeout {
  private static final String TIMEOUT_STRING = "timeout";
  private static final String SPACE = " ";
  private static final String ERROR_HELPER =
      "Valid timeouts contain units s/m/h/d/w and are written like 10s or 1m 10s";
  private static final Long DEFAULT_TIMEOUT_IN_MILLIS = Duration.ofHours(10).toMillis();

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

    public static Optional<TimeUnit> findByUnit(String unit) {
      return Stream.of(TimeUnit.values()).filter(timeUnit -> timeUnit.suffix.equals(unit)).findFirst();
    }
  }

  private String timeoutString;
  private long timeoutInMillis;

  @JsonCreator
  public static Timeout fromString(String timeout) {
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
          throw new InvalidArgumentsException(Pair.of(TIMEOUT_STRING, timeout), ERROR_HELPER);
        }

        prevValidSymbol = currentSymbol;
      }

      if (currentValue != 0 || !isUnitCharacter(prevValidSymbol)) {
        throw new InvalidArgumentsException(Pair.of(TIMEOUT_STRING, timeout), ERROR_HELPER);
      }

      totalValue = totalValue != 0 ? totalValue : DEFAULT_TIMEOUT_IN_MILLIS;
      return Timeout.builder().timeoutInMillis(totalValue).timeoutString(timeout).build();

    } catch (NumberFormatException e) {
      throw new InvalidArgumentsException(Pair.of(TIMEOUT_STRING, timeout), e);
    }
  }

  private static boolean isDigit(String symbol) {
    return isNotEmpty(symbol) && Character.isDigit(symbol.charAt(0));
  }

  private static boolean isUnitCharacter(String symbol) {
    return TimeUnit.findByUnit(symbol).isPresent();
  }

  private static long getMultiplyCoefficient(String symbol, String timeout) {
    return TimeUnit.findByUnit(symbol)
        .orElseThrow(() -> new InvalidArgumentsException(Pair.of(TIMEOUT_STRING, timeout)))
        .coefficient;
  }

  @JsonValue
  public String getYamlProperty() {
    return timeoutString;
  }
}
