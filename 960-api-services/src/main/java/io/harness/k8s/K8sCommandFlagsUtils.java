/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.data.structure.EmptyPredicate;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sCommandFlagsUtils {
  public String applyK8sCommandFlags(String commandType, Map<K8sSubCommandType, String> commandFlags) {
    String flags = "";
    if (isNotEmpty(commandFlags)) {
      K8sSubCommandType subCommandType = K8sSubCommandType.getSubCommandType(commandType);
      flags = commandFlags.getOrDefault(subCommandType, "");
      if (EmptyPredicate.isEmpty(flags)) {
        flags = "";
      }
    }

    return flags;
  }
}
