/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import io.harness.encryption.Scope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FreezeServiceHelper {
  public Map<Scope, List<String>> getMapForEachScope() {
    Map<Scope, List<String>> manualFreezeIdentifiersForEachScope = new HashMap<>();
    manualFreezeIdentifiersForEachScope.put(Scope.ACCOUNT, new ArrayList<>());
    manualFreezeIdentifiersForEachScope.put(Scope.ORG, new ArrayList<>());
    manualFreezeIdentifiersForEachScope.put(Scope.PROJECT, new ArrayList<>());
    return manualFreezeIdentifiersForEachScope;
  }
}
