/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.pcf.mappers.TasInstanceIndexToServerInstanceInfoMapper;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class TasTaskHelperBase {
  @Inject private TasNgConfigMapper ngConfigMapper;
  @Inject protected CfDeploymentManager pcfDeploymentManager;

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  public List<ServerInstanceInfo> getTasServerInstanceInfos(TasDeploymentReleaseData deploymentReleaseData) {
    TasInfraConfig tasInfraConfig = deploymentReleaseData.getTasInfraConfig();
    CloudFoundryConfig cfConfig = ngConfigMapper.mapTasConfigWithDecryption(
        tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    try {
      CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                            .timeOutIntervalInMins(5)
                                            .applicationName(deploymentReleaseData.getApplicationName())
                                            .userName(String.valueOf(cfConfig.getUserName()))
                                            .password(String.valueOf(cfConfig.getPassword()))
                                            .endpointUrl(cfConfig.getEndpointUrl())
                                            .orgName(tasInfraConfig.getOrganization())
                                            .spaceName(tasInfraConfig.getSpace())
                                            .build();

      ApplicationDetail applicationDetail = pcfDeploymentManager.getApplicationByName(cfRequestConfig);
      List<String> instanceIndices =
          applicationDetail.getInstanceDetails().stream().map(InstanceDetail::getIndex).collect(toList());

      return TasInstanceIndexToServerInstanceInfoMapper.toServerInstanceInfoList(
          instanceIndices, tasInfraConfig, applicationDetail);

    } catch (Exception e) {
      log.warn("Failed while collecting TAS Application Details For Application: {}, with Error: {}",
          deploymentReleaseData.getApplicationName(), e);
      return Collections.emptyList();
    }
  }
}
