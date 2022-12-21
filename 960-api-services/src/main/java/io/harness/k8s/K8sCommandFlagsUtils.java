/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sCommandFlagsUtils {
  public String getK8sCommandFlags(String commandType, Map<String, String> commandFlags) {
    if (isNotEmpty(commandFlags)) {
      String flags = commandFlags.get(commandType);
      return isNotEmpty(flags) ? flags : "";
    }

    return "";
  }
}
