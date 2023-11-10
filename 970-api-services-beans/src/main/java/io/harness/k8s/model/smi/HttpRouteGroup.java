/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.smi;

import static io.harness.k8s.model.smi.Constants.HTTP_ROUTE_GROUP;

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
public class HttpRouteGroup extends SMIRoute {
  @Builder
  private HttpRouteGroup(String apiVersion, Metadata metadata, RouteSpec spec) {
    super(apiVersion, HTTP_ROUTE_GROUP, metadata, spec);
    validate();
  }
  private void validate() {
    if (spec.getMatches().stream().noneMatch(
            match -> match instanceof URIMatch || match instanceof MethodMatch || match instanceof HeaderMatch)) {
      throw new IllegalArgumentException("Unsupported spec in the HttpRouteGroup");
    }
  }
}
