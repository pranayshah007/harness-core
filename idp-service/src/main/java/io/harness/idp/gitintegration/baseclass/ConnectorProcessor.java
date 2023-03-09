/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.baseclass;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
public abstract class ConnectorProcessor {
  @Inject public ConnectorResourceClient connectorResourceClient;
  @Inject @Named("PRIVILEGED") public SecretManagerClientService ngSecretService;

  public abstract String getInfraConnectorType(String accountIdentifier, String connectorIdentifier);

  public abstract Pair<ConnectorInfoDTO, List<EnvironmentSecret>> getConnectorSecretsInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier);

  public abstract void performPushOperation(
      String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo, List<String> locationToPush);
}
