/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.gitintegration.service;

import io.harness.delegate.beans.connector.ConnectorType;

public interface GitIntegrationService {
  public boolean createConnectorsSecretsEnvVariable(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, ConnectorType connectorType) throws Exception;
}
