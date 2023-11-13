/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.istio;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class VirtualService {
  private static final String VIRTUAL_SERVICE_KIND = "VirtualService";
  @NotNull String kind;
  @NotNull String apiVersion;
  @NotNull Metadata metadata;
  @NotNull VirtualServiceSpec spec;
  @Builder
  private VirtualService(String apiVersion, Metadata metadata, VirtualServiceSpec spec) {
    this.apiVersion = apiVersion;
    this.metadata = metadata;
    this.spec = spec;
    this.kind = VIRTUAL_SERVICE_KIND;
  }
}
