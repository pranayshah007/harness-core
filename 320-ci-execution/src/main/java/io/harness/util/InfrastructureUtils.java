/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.util;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class InfrastructureUtils {
  public Optional<ParameterField<String>> getHarnessImageConnector(Infrastructure infrastructure) {
    ParameterField<String> harnessImageConnector = null;
    switch (infrastructure.getType()) {
      case KUBERNETES_DIRECT:
        harnessImageConnector = ((K8sDirectInfraYaml) infrastructure).getSpec().getHarnessImageConnectorRef();
        break;
      case VM:
        VmInfraSpec vmInfraSpec = ((VmInfraYaml) infrastructure).getSpec();
        if (vmInfraSpec instanceof VmPoolYaml) {
          harnessImageConnector = ((VmPoolYaml) vmInfraSpec).getSpec().getHarnessImageConnectorRef();
        }
        break;
      default:
        break;
    }
    return ParameterField.isNull(harnessImageConnector) ? Optional.empty() : Optional.of(harnessImageConnector);
  }
}