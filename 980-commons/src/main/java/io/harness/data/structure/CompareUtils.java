/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.structure;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CompareUtils {
  public boolean compareNullableStrings(String string1, String string2) {
    if (isEmpty(string1) && isEmpty(string2)) {
      return true;
    }
    if (isEmpty(string1) || isEmpty(string2)) {
      return false;
    }
    return string1.compareTo(string2) == 0;
  }

  public boolean compareObjects(Object object1, Object object2) {
    if (object1 == null && object2 == null) {
      return true;
    }
    if (object2 == null || object1 == null) {
      return false;
    }
    return object1.equals(object2);
  }
}
