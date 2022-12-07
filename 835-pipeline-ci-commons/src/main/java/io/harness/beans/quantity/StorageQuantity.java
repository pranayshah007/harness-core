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
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.exception.InvalidArgumentsException;
import io.harness.yaml.extended.ci.validator.ResourceValidatorConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(CI)
@TypeAlias("storageQuantity")
@RecasterAlias("io.harness.beans.quantity.StorageQuantity")
public class StorageQuantity {
  private String numericValue;
  private StorageQuantityUnit unit;

  @JsonCreator
  public static StorageQuantity fromString(String quantity) {
    if (isEmpty(quantity)) {
      return null;
    }

    Pattern r = Pattern.compile(ResourceValidatorConstants.STORAGE_PATTERN);
    Matcher m = r.matcher(quantity);
    if (m.find()) {
      String numericValue = m.group(1);
      String suffix = m.group(3);
      StorageQuantityUnit unit = Stream.of(StorageQuantityUnit.values())
                                     .filter(quantityUnit -> quantityUnit.getSuffix().equals(suffix))
                                     .findFirst()
                                     .orElse(StorageQuantityUnit.unitless);

      return StorageQuantity.builder().numericValue(numericValue).unit(unit).build();
    } else {
      throw new InvalidArgumentsException(String.format(
          "Invalid format: %s. value should match regex: %s", quantity, ResourceValidatorConstants.STORAGE_PATTERN));
    }
  }

  @JsonValue
  public String getYamlProperty() {
    return numericValue + unit.getSuffix();
  }
}
