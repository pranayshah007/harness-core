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

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class TCPVirtualServiceSpec extends VirtualServiceSpec {
  @NotNull List<VirtualServiceDetails> tcp;

  @Builder
  private TCPVirtualServiceSpec(List<String> hosts, List<String> gateways, List<VirtualServiceDetails> tcp) {
    super(hosts, gateways);
    this.tcp = tcp;
    validate();
  }

  private void validate() {
    if (tcp.stream().anyMatch(vs -> vs.getMatch().stream().anyMatch(match -> !(match instanceof PortMatch)))) {
      throw new IllegalArgumentException(
          "Unsupported spec in the VirtualService. Only Port match is supported for TCP type");
    }
  }
}
