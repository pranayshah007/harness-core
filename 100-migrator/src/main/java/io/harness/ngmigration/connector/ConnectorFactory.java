/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.DockerConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;

@OwnedBy(HarnessTeam.CDC)
public class ConnectorFactory {
  private static final BaseConnector dockerConnector = new DockerConnectorImpl();
  private static final BaseConnector kubernetesConnector = new KubernetesConnectorImpl();
  private static final BaseConnector gitConnector = new GitConnectorImpl();
  private static final BaseConnector unsupportedConnector = new UnsupportedConnectorImpl();

  public static BaseConnector getConnector(SettingAttribute settingAttribute) {
    if (settingAttribute.getValue() instanceof DockerConfig) {
      return dockerConnector;
    }
    if (settingAttribute.getValue() instanceof KubernetesClusterConfig) {
      return kubernetesConnector;
    }
    if (settingAttribute.getValue() instanceof GitConfig) {
      return gitConnector;
    }
    return unsupportedConnector;
  }
}
