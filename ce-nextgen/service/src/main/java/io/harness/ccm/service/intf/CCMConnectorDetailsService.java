/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.commons.entities.CCMConnectorDetails;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;

import java.util.List;

public interface CCMConnectorDetailsService {
  List<ConnectorResponseDTO> listNgConnectors(String accountId, ConnectivityStatus status);
  CCMConnectorDetails getFirstConnectorDetails(String accountId);
}
