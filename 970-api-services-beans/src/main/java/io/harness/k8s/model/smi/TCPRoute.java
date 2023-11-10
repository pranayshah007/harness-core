/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.smi;

import static io.harness.k8s.model.smi.Constants.TCP_ROUTE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class TCPRoute extends SMIRoute {
  @Builder
  public TCPRoute(String apiVersion, Metadata metadata, RouteSpec spec) {
    super(apiVersion, TCP_ROUTE, metadata, spec);
    validate();
  }
  private void validate() {
    if (spec.getMatches().stream().noneMatch(PortMatch.class ::isInstance)) {
      throw new IllegalArgumentException("Unsupported spec in the TCPRoute");
    }
  }
}
