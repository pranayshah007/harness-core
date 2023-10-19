/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.EmptyPredicate.IsEmpty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseMetadata implements IsEmpty {
  String serviceId;
  String infraId;
  @EqualsAndHashCode.Exclude String infraKey;
  String envId;

  public boolean isEmpty() {
    return EmptyPredicate.isEmpty(serviceId) && EmptyPredicate.isEmpty(infraId) && EmptyPredicate.isEmpty(infraKey)
        && EmptyPredicate.isEmpty(envId);
  }
}
