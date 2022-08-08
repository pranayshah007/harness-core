/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.userprofile.commons;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorType;

import java.util.Map;
import java.util.stream.Stream;

@OwnedBy(PL)
public enum SCMType {
  BITBUCKET(ConnectorType.BITBUCKET),
  GITHUB(ConnectorType.GITHUB),
  GITLAB(ConnectorType.GITLAB),
  AWS_CODE_COMMIT(ConnectorType.CODECOMMIT),
  AZURE_REPO(ConnectorType.AZURE_REPO);

  private final ConnectorType connectorType;

  SCMType(ConnectorType connectorType) {
    this.connectorType = connectorType;
  }

  public ConnectorType getConnectorType() {
    return connectorType;
  }

  private static final Map<ConnectorType, SCMType> connectorTypeToSCMType =
      Stream.of(values()).collect(toMap(SCMType::getConnectorType, e -> e));

  public static SCMType fromConnectorType(ConnectorType connectorType) {
    return connectorTypeToSCMType.get(connectorType);
  }
}
