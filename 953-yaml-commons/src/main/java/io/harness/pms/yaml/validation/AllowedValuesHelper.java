/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class AllowedValuesHelper {
  public List<String> split(String input) {
    List<String> values = new ArrayList<>();
    int startPosition = 0;
    boolean isInSingleQuotes = false;
    boolean isInDoubleQuotes = false;

    for (int currentPosition = 0; currentPosition < input.length(); currentPosition++) {
      // Checking if the current position has substring \'
      if (currentPosition + 1 < input.length() && input.charAt(currentPosition) == '\\'
          && input.charAt(currentPosition + 1) == '\'') {
        isInSingleQuotes = !isInSingleQuotes;
      }
      // Checking if the current position has substring \"
      else if (currentPosition + 1 < input.length() && input.charAt(currentPosition) == '\\'
          && input.charAt(currentPosition + 1) == '\"') {
        isInDoubleQuotes = !isInDoubleQuotes;
      }
      // If current char is , and it's not within single or double quotes, then add the substring to the list
      else if (input.charAt(currentPosition) == ',' && !isInSingleQuotes && !isInDoubleQuotes) {
        values.add(input.substring(startPosition, currentPosition).trim());
        startPosition = currentPosition + 1;
      }
    }

    // Add last value after the last comma to the list
    String lastValue = input.substring(startPosition);
    values.add(lastValue.trim());

    // remove outer quotes from \" ... \" and \' ... \'
    for (int i = 0; i < values.size(); i++) {
      if ((values.get(i).startsWith("\\\"") && values.get(i).endsWith("\\\""))
          || (values.get(i).startsWith("\\'") && values.get(i).endsWith("\\'"))) {
        values.set(i, values.get(i).substring(2, values.get(i).length() - 2));
      }
    }
    return values;
  }
}
