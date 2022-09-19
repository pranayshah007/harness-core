/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static software.wings.security.EnvFilter.FilterType.NON_PROD;
import static software.wings.security.EnvFilter.FilterType.PROD;
import static software.wings.security.GenericEntityFilter.FilterType.ALL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.config.CCMConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.event.handler.impl.segment.SegmentHelper;

import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.intfc.SettingsService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.segment.analytics.messages.TrackMessage;
import java.util.Collections;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class KubernetesClusterHandler implements DelegateObserver {
  @Inject SettingsService settingsService;
  @Inject SegmentHelper segmentHelper;
  @Inject ConnectorResourceClient connectorResourceClient;

  @Override
  public void onAdded(Delegate delegate) {
    if (delegate.isNg()) {
      // create NextGen CD-K8s connector
      connectorResourceClient.createConnector(delegate.getAccountId(),
          ConnectorDTO.builder()
              .connectorInfo(
                  ConnectorInfoDTO.builder()
                      .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                      .name(delegate.getDelegateName())
                      .identifier(delegate.getDelegateName())
                      .connectorConfig(
                          KubernetesClusterConfigDTO.builder()
                              .delegateSelectors(ImmutableSet.of(delegate.getDelegateName()))
                              .credential(KubernetesCredentialDTO.builder()
                                              .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                                              .build())
                              .build())
                      .build())
              .build());

      // create NextGen CE-K8s connector
      connectorResourceClient.createConnector(delegate.getAccountId(),
          ConnectorDTO.builder()
              .connectorInfo(ConnectorInfoDTO.builder()
                                 .connectorType(ConnectorType.CE_KUBERNETES_CLUSTER)
                                 .connectorConfig(CEKubernetesClusterConfigDTO.builder()
                                                      .connectorRef(delegate.getDelegateName())
                                                      .featuresEnabled(Collections.singletonList(CEFeatures.VISIBILITY))
                                                      .build())
                                 .identifier(delegate.getDelegateName() + "Costaccess")
                                 .name(delegate.getDelegateName() + "-Cost-access")
                                 .build())
              .build());
    } else {
      createKubernetes(delegate);
    }
    segmentHelper.enqueue(TrackMessage.builder("Delegate Connected")
                              .properties(ImmutableMap.<String, Object>builder()
                                              .put("accountId", delegate.getAccountId())
                                              .put("delegateId", delegate.getUuid())
                                              .put("delegateName", delegate.getDelegateName())
                                              .put("product", "Cloud Cost Management")
                                              .build())
                              .anonymousId(delegate.getAccountId()));
  }

  @Override
  public void onDisconnected(String accountId, String delegateId) {
    // do nothing
  }

  @Override
  public void onReconnected(String accountId, String delegateId) {
    // do nothing
  }

  private void createKubernetes(Delegate delegate) {
    KubernetesClusterConfig kubernetesClusterConfig =
        KubernetesClusterConfig.builder()
            .accountId(delegate.getAccountId())
            .useKubernetesDelegate(true)
            .delegateSelectors(new HashSet<>(Collections.singletonList(delegate.getDelegateName())))
            .skipValidation(true)
            .ccmConfig(CCMConfig.builder().cloudCostEnabled(true).skipK8sEventCollection(false).build())
            .build();
    settingsService.save(
        SettingAttribute.Builder.aSettingAttribute()
            .withAccountId(delegate.getAccountId())
            .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
            .withValue(kubernetesClusterConfig)
            .withName(delegate.getDelegateName())
            .withUsageRestrictions(UsageRestrictions.builder()
                                       .appEnvRestrictions(ImmutableSet.of(
                                           UsageRestrictions.AppEnvRestriction.builder()
                                               .appFilter(GenericEntityFilter.builder().filterType(ALL).build())
                                               .envFilter(new EnvFilter(null, Sets.newHashSet(PROD)))
                                               .build(),
                                           UsageRestrictions.AppEnvRestriction.builder()
                                               .appFilter(GenericEntityFilter.builder().filterType(ALL).build())
                                               .envFilter(new EnvFilter(null, Sets.newHashSet(NON_PROD)))
                                               .build()))
                                       .build())
            .build());
    log.info("Created a Kubernetes cloud provider based on the in-cluster Delegate {}", delegate.getDelegateName());
  }
}
