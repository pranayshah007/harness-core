/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.utils;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(DX)
@UtilityClass
public class ConnectorUtils {
  public void checkForConnectorValidityOrThrow(ConnectorResponseDTO connector) {
    if (!connector.getEntityValidityDetails().isValid()) {
      throw new InvalidRequestException(
          format("Connector for identifier [%s] is invalid. Please fix the connector YAML.",
              connector.getConnector().getIdentifier()),
          WingsException.USER);
    }
  }
  public boolean isValid(ConnectorResponseDTO connector) {
    return connector.getEntityValidityDetails().isValid();
  }
}
