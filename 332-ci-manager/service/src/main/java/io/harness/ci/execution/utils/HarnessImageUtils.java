/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
@OwnedBy(HarnessTeam.CI)
public class HarnessImageUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  public ConnectorDetails getHarnessImageConnectorDetailsForK8(NGAccess ngAccess, Infrastructure infrastructure) {
    ConnectorDetails harnessInternalImageConnector = null;
    Optional<ParameterField<String>> optionalHarnessImageConnector =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);
    if (optionalHarnessImageConnector.isPresent()) {
      harnessInternalImageConnector =
          connectorUtils.getConnectorDetails(ngAccess, optionalHarnessImageConnector.get().getValue());
    } else if (isNotEmpty(ciExecutionServiceConfig.getDefaultInternalImageConnector())) {
      harnessInternalImageConnector = connectorUtils.getDefaultInternalConnector(ngAccess);
    }
    return harnessInternalImageConnector;
  }

  public ConnectorDetails getHarnessImageConnectorDetailsForVM(NGAccess ngAccess, StageInfraDetails stageInfraDetails) {
    ConnectorDetails harnessInternalImageConnector = null;
    String harnessImageConnectorRef = getVmHarnessImageConnectorRef(stageInfraDetails);
    if (isNotEmpty(harnessImageConnectorRef)) {
      harnessInternalImageConnector = connectorUtils.getConnectorDetails(ngAccess, harnessImageConnectorRef);
    } else if (isNotEmpty(ciExecutionServiceConfig.getDefaultInternalImageConnector())) {
      harnessInternalImageConnector = connectorUtils.getDefaultInternalConnector(ngAccess);
    }
    return harnessInternalImageConnector;
  }

  private String getVmHarnessImageConnectorRef(StageInfraDetails stageInfraDetails) {
    StageInfraDetails.Type type = stageInfraDetails.getType();
    if (type == StageInfraDetails.Type.VM) {
      VmStageInfraDetails vmStageInfraDetails = (VmStageInfraDetails) stageInfraDetails;
      return vmStageInfraDetails.getHarnessImageConnectorRef();
    } else if (type == StageInfraDetails.Type.DLITE_VM) {
      DliteVmStageInfraDetails dliteVmStageInfraDetails = (DliteVmStageInfraDetails) stageInfraDetails;
      return dliteVmStageInfraDetails.getHarnessImageConnectorRef();
    } else {
      throw new InvalidRequestException("Harness image step evaluation only allowed for VM.");
    }
  }
}
