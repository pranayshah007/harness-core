/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.baseclass;

import io.harness.connector.ConnectorResourceClient;
import io.harness.idp.secret.beans.dto.EnvironmentSecretValueDTO;

import java.util.List;
import javax.inject.Inject;

public abstract class ConnectorProcessor {
  @Inject public ConnectorResourceClient connectorResourceClient;

  abstract public List<EnvironmentSecretValueDTO> getConnectorSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier);
}
