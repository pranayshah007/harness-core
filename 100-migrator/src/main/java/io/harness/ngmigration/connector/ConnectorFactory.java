/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static software.wings.settings.SettingVariableTypes.ARTIFACTORY;
import static software.wings.settings.SettingVariableTypes.AZURE;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.settings.SettingVariableTypes.GIT;
import static software.wings.settings.SettingVariableTypes.HTTP_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;
import static software.wings.settings.SettingVariableTypes.NEXUS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.SettingAttribute;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ConnectorFactory {
  private static final BaseConnector artifactoryConnector = new ArtifactoryConnectorImpl();
  private static final BaseConnector nexusConnector = new NexusConnectorImpl();
  private static final BaseConnector dockerConnector = new DockerConnectorImpl();
  private static final BaseConnector kubernetesConnector = new KubernetesConnectorImpl();
  private static final BaseConnector gitConnector = new GitConnectorImpl();
  private static final BaseConnector gcpConnector = new GcpConnectorImpl();
  private static final BaseConnector azureConnector = new AzureConnectorImpl();
  private static final BaseConnector httpHelmConnector = new HttpHelmConnectorImpl();
  private static final BaseConnector unsupportedConnector = new UnsupportedConnectorImpl();

  public static final Map<SettingVariableTypes, BaseConnector> CONNECTOR_FACTORY_MAP =
      ImmutableMap.<SettingVariableTypes, BaseConnector>builder()
          .put(NEXUS, nexusConnector)
          .put(ARTIFACTORY, artifactoryConnector)
          .put(DOCKER, dockerConnector)
          .put(KUBERNETES_CLUSTER, kubernetesConnector)
          .put(GIT, gitConnector)
          .put(GCP, gcpConnector)
          .put(AZURE, azureConnector)
          .put(HTTP_HELM_REPO, httpHelmConnector)
          .build();

  public static BaseConnector getConnector(SettingAttribute settingAttribute) {
    SettingVariableTypes settingVariableTypes = settingAttribute.getValue().getSettingType();
    if (CONNECTOR_FACTORY_MAP.containsKey(settingVariableTypes)) {
      return CONNECTOR_FACTORY_MAP.get(settingAttribute.getValue().getSettingType());
    }
    return unsupportedConnector;
  }
}
