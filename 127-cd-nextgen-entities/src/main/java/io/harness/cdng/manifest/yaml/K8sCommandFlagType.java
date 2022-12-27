/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.k8s.ServiceSpecType;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;

@Getter
@OwnedBy(CDP)
public enum K8sCommandFlagType {
  Apply("Apply", ImmutableSet.of(ServiceSpecType.NATIVE_HELM, ServiceSpecType.KUBERNETES));

  private final String subCommandType;
  private final Set<String> serviceSpecTypes;
  K8sCommandFlagType(String subCommandType, Set<String> serviceSpecTypes) {
    this.subCommandType = subCommandType;
    this.serviceSpecTypes = serviceSpecTypes;
  }
}
