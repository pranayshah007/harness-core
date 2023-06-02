/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.ci.utils.BaseConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.ng.core.NGAccess;
import io.harness.steps.container.execution.ContainerExecutionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ConnectorUtils extends BaseConnectorUtils {
  @Inject
  public ConnectorUtils(ContainerExecutionConfig containerExecutionConfig) {
    this.containerExecutionConfig = containerExecutionConfig;
  }

  private final ContainerExecutionConfig containerExecutionConfig;

  public ConnectorDetails getDefaultInternalConnector(NGAccess ngAccess) {
    ConnectorDetails connectorDetails = null;
    try {
      connectorDetails = getConnectorDetails(ngAccess, containerExecutionConfig.getDefaultInternalImageConnector());
    } catch (ConnectorNotFoundException e) {
      log.info("Default harness image connector does not exist: {}", e.getMessage());
      connectorDetails = null;
    }
    return connectorDetails;
  }

  public ConnectorDetails getConnectorDetailsWithConversionInfo(
      NGAccess ngAccess, ConnectorConversionInfo connectorConversionInfo) {
    ConnectorDetails connectorDetails = getConnectorDetails(ngAccess, connectorConversionInfo.getConnectorRef());
    connectorDetails.setEnvToSecretsMap(connectorConversionInfo.getEnvToSecretsMap());
    return connectorDetails;
  }
}
