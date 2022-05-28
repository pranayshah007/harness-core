/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.util;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.ConnectorUtils;

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
    if (!(stageInfraDetails instanceof VmStageInfraDetails)) {
      throw new InvalidRequestException("Harness image step evaluation only allowed for VM.");
    }
    VmStageInfraDetails vmStageInfraDetails = (VmStageInfraDetails) stageInfraDetails;
    ConnectorDetails harnessInternalImageConnector = null;
    if (isNotEmpty(vmStageInfraDetails.getHarnessImageConnectorRef())) {
      harnessInternalImageConnector =
          connectorUtils.getConnectorDetails(ngAccess, vmStageInfraDetails.getHarnessImageConnectorRef());
    } else if (isNotEmpty(ciExecutionServiceConfig.getDefaultInternalImageConnector())) {
      harnessInternalImageConnector = connectorUtils.getDefaultInternalConnector(ngAccess);
    }
    return harnessInternalImageConnector;
  }
}
