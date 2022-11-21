/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.GcpConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.infrastructure.Host;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureProvider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

/**
 * Created by brett on 2/27/17
 */
@Singleton
public class GcpInfrastructureProvider implements InfrastructureProvider {
  @Inject private HostService hostService;
  @Inject private DelegateProxyFactory delegateProxyFactory;

  @Override
  public PageResponse<Host> listHosts(AwsInfrastructureMapping awsInfrastructureMapping,
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails, PageRequest<Host> req) {
    return aPageResponse().withResponse(null).build();
  }

  @Override
  public PageResponse<Host> listHosts(InfrastructureDefinition infrastructureDefinition,
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails, PageRequest<Host> req) {
    return aPageResponse().withResponse(null).build();
  }

  @Override
  public void deleteHost(String appId, String infraMappingId, String dnsName) {
    hostService.deleteByDnsName(appId, infraMappingId, dnsName);
  }

  @Override
  public void updateHostConnAttrs(InfrastructureMapping infrastructureMapping, String hostConnectionAttrs) {
    hostService.updateHostConnectionAttrByInfraMapping(infrastructureMapping, hostConnectionAttrs);
  }

  @Override
  public Host saveHost(Host host) {
    return hostService.saveHost(host);
  }

  List<String> listClusterNames(
      SettingAttribute computeProviderSetting, List<EncryptedDataDetail> encryptedDataDetails) {
    checkValidSettingAttribute(computeProviderSetting);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(computeProviderSetting.getAccountId())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    return delegateProxyFactory.get(ContainerService.class, syncTaskContext)
        .listClusters(ContainerServiceParams.builder()
                          .encryptionDetails(encryptedDataDetails)
                          .settingAttribute(computeProviderSetting.toDTO())
                          .clusterName("None")
                          .build());
  }

  private void checkValidSettingAttribute(SettingAttribute computeProviderSetting) {
    if (computeProviderSetting.getValue() instanceof GcpConfig
        && ((GcpConfig) computeProviderSetting.getValue()).isUseDelegateSelectors()) {
      throw new InvalidRequestException(
          "Infrastructure Definition Using a GCP Cloud Provider Inheriting from Delegate is not yet supported", USER);
    }
  }
}
