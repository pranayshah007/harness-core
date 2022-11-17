/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;

@Getter
public enum K8sSubCommandType {
  APPLY(ImmutableSet.of(K8sCliCommandType.APPLY.name()));

  private final Set<String> commandTypes;

  K8sSubCommandType(Set<String> commandTypes) {
    this.commandTypes = commandTypes;
  }

  public static K8sSubCommandType getSubCommandType(String commandType) {
    for (K8sSubCommandType subCommandType : K8sSubCommandType.values()) {
      if (subCommandType.getCommandTypes().contains(commandType)) {
        return subCommandType;
      }
    }
    return null;
  }
}
