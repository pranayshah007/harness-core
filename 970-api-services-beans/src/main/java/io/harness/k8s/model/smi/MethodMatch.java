/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.smi;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class MethodMatch extends Match {
  List<String> methods;

  @Builder
  private MethodMatch(String name, List<String> methods) {
    super(name);
    this.methods = methods;
    validate();
  }

  private void validate() {
    if (!new HashSet<>(Stream.of(Method.values()).map(Enum::name).toList()).containsAll(methods)) {
      throw new IllegalArgumentException("Unsupported Method in the HttpRouteGroup");
    }
  }

  private enum Method { GET, POST, PUT, DELETE, HEAD, CONNECT, OPTION, TRACE, PATCH }
}
