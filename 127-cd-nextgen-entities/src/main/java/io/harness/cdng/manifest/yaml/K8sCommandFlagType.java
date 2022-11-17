/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.k8s.K8sSubCommandType;
import io.harness.ng.core.k8s.ServiceSpecType;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;

@Getter
public enum K8sCommandFlagType {
  Apply(K8sSubCommandType.APPLY, ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES));

  private final K8sSubCommandType subCommandType;
  private final Set<String> serviceSpecTypes;
  K8sCommandFlagType(K8sSubCommandType subCommandType, Set<String> serviceSpecTypes) {
    this.subCommandType = subCommandType;
    this.serviceSpecTypes = serviceSpecTypes;
  }
}
