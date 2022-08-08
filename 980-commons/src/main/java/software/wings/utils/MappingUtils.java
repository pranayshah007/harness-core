/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class MappingUtils {
  public static Map<String, String> safeCopy(Map<String, String> map) {
    HashMap<String, String> recreated = new HashMap<>();
    if (EmptyPredicate.isEmpty(map)) {
      return map;
    }
    map.keySet().forEach(key -> recreated.put(key, map.get(key)));
    return recreated;
  }
}
