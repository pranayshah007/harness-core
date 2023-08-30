/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.data.structure;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/*
Aim is to make sure that java 8 String.join is used over Guava Joiner class
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class HarnessStringUtils {
  public static String join(@NonNull CharSequence delimiter, @NonNull CharSequence... elements) {
    return String.join(delimiter, elements);
  }

  public static String join(@NonNull CharSequence delimiter, @NonNull Iterable<? extends CharSequence> elements) {
    return String.join(delimiter, elements);
  }
  public static String nullIfEmpty(String stringInput) {
    return isEmpty(stringInput) ? null : stringInput;
  }
  public static String emptyIfNull(String stringInput) {
    return stringInput == null ? "" : stringInput;
  }
  public static String removeLeadingAndTrailingQuotesBothOrNone(String str) {
    if (str.length() > 1 && str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"') {
      return str.substring(1, str.length() - 1);
    }
    return str;
  }
  public List<String> removeLeadingAndTrailingSpacesInListOfStrings(List<String> list) {
    List<String> sanitisedList = new ArrayList<>();
    for (String elem : list) {
      sanitisedList.add(elem.trim());
    }
    return sanitisedList;
  }
}
