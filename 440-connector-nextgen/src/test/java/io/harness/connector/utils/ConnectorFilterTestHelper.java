/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.utils;

import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorConnectivityMode;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.entities.ConnectorFilterProperties;
import io.harness.delegate.beans.connector.ConnectorType;

import java.util.Arrays;
import java.util.Collections;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ConnectorFilterTestHelper {
  public ConnectorFilterPropertiesDTO createConnectorFilterPropertiesDTOForTest() {
    return ConnectorFilterPropertiesDTO.builder()
        .categories(Collections.singletonList(ConnectorCategory.CLOUD_PROVIDER))
        .connectorNames(Arrays.asList("Connector 1", "Connector 2"))
        .connectivityStatuses(Collections.singletonList(ConnectivityStatus.SUCCESS))
        .connectorIdentifiers(Arrays.asList("Connector identifier 1", "Connector identifier 2"))
        .description("Connector description 1")
        .inheritingCredentialsFromDelegate(true)
        .types(Collections.singletonList(ConnectorType.KUBERNETES_CLUSTER))
        .connectorConnectivityModes(Collections.singletonList(ConnectorConnectivityMode.DELEGATE))
        .build();
  }

  public ConnectorFilterProperties createConnectorFilterPropertiesEntityForTest() {
    return ConnectorFilterProperties.builder()
        .categories(Collections.singletonList(ConnectorCategory.CLOUD_PROVIDER))
        .connectorNames(Arrays.asList("Connector 1", "Connector 2"))
        .connectivityStatuses(Collections.singletonList(ConnectivityStatus.SUCCESS))
        .connectorIdentifiers(Arrays.asList("Connector identifier 1", "Connector identifier 2"))
        .description("Connector description 1")
        .inheritingCredentialsFromDelegate(true)
        .types(Collections.singletonList(ConnectorType.KUBERNETES_CLUSTER))
        .connectorConnectivityModes(Collections.singletonList(ConnectorConnectivityMode.DELEGATE))
        .build();
  }
}
