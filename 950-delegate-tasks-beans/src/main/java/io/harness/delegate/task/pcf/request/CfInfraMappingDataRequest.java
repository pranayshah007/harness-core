/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.pcf.model.CfCliVersion;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
public class CfInfraMappingDataRequest extends CfCommandRequest {
  private CfInternalConfig pcfConfig;
  private String host;
  private String domain;
  private String path;
  private Integer port;
  private boolean useRandomPort;
  private boolean tcpRoute;
  private String applicationNamePrefix;
  private ActionType actionType;
  public enum ActionType { RUNNING_COUNT, FETCH_ORG, FETCH_SPACE, FETCH_ROUTE }

  @Builder
  public CfInfraMappingDataRequest(String accountId, String appId, String commandName, String activityId,
      PcfCommandType pcfCommandType, String organization, String space, CfInternalConfig pcfConfig,
      String workflowExecutionId, Integer timeoutIntervalInMin, String host, String domain, String path, Integer port,
      boolean useRandomPort, boolean tcpRoute, String applicationNamePrefix, ActionType actionType,
      boolean useCLIForPcfAppCreation, boolean limitPcfThreads, CfCliVersion cfCliVersion) {
    super(accountId, appId, commandName, activityId, pcfCommandType, organization, space, pcfConfig,
        workflowExecutionId, timeoutIntervalInMin, useCLIForPcfAppCreation, false, false, limitPcfThreads,
        cfCliVersion);
    this.pcfConfig = pcfConfig;
    this.host = host;
    this.domain = domain;
    this.path = path;
    this.port = port;
    this.useRandomPort = useRandomPort;
    this.tcpRoute = tcpRoute;
    this.applicationNamePrefix = applicationNamePrefix;
    this.actionType = actionType;
  }
}
