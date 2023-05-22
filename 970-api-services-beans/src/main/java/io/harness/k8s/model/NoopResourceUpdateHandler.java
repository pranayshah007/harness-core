/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(CDP)
public class NoopResourceUpdateHandler implements KubernetesResourceUpdateHandler {
  @Override
  public void onNameChange(
      KubernetesResource kubernetesResource, KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    // this is a default handler
  }

  @Override
  public void onSelectorsChange(
      KubernetesResource kubernetesResource, KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    // this is a default handler
  }
}
