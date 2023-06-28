/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ccm.commons.entities.CCMConnectorDetails;
import io.harness.ccm.connectors.AbstractCEConnectorValidator;
import io.harness.ccm.connectors.CEConnectorValidatorFactory;
import io.harness.ccm.service.intf.CCMConnectorDetailsService;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCMConnectorDetailsServiceImpl implements CCMConnectorDetailsService {
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private CEConnectorValidatorFactory ceConnectorValidatorFactory;

  @Override
  @NonNull
  public List<ConnectorResponseDTO> listNgConnectors(String accountId, List<ConnectorType> connectorTypes,
      List<CEFeatures> ceFeatures, List<ConnectivityStatus> connectivityStatuses) {
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO = ConnectorFilterPropertiesDTO.builder().build();
    if (isNotEmpty(connectorTypes)) {
      connectorFilterPropertiesDTO.setTypes(connectorTypes);
    }
    if (isNotEmpty(ceFeatures)) {
      connectorFilterPropertiesDTO.setCcmConnectorFilter(
          CcmConnectorFilter.builder().featuresEnabled(ceFeatures).build());
    }
    if (isNotEmpty(connectivityStatuses)) {
      connectorFilterPropertiesDTO.setConnectivityStatuses(connectivityStatuses);
    }
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);

    int page = 0;
    int size = 100;
    do {
      response = NGRestUtils.getResponse(connectorResourceClient.listConnectors(
          accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectorResponses.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));
    return nextGenConnectorResponses;
  }

  @Override
  @Nullable
  public CCMConnectorDetails getFirstConnectorDetails(String accountId) {
    List<ConnectorResponseDTO> ngHealthyConnectors =
        listNgConnectors(accountId, null, Arrays.asList(CEFeatures.BILLING), Arrays.asList(ConnectivityStatus.SUCCESS));
    ngHealthyConnectors.sort(Comparator.comparing(ConnectorResponseDTO::getCreatedAt));
    if (!ngHealthyConnectors.isEmpty()) {
      return getConnectorDetails(ngHealthyConnectors.get(0), accountId);
    }

    List<ConnectorResponseDTO> ngUnhealthyConnectors =
        listNgConnectors(accountId, null, Arrays.asList(CEFeatures.BILLING), Arrays.asList(ConnectivityStatus.FAILURE));
    ngHealthyConnectors.sort(Comparator.comparing(ConnectorResponseDTO::getCreatedAt));
    if (!ngUnhealthyConnectors.isEmpty()) {
      return getConnectorDetails(ngUnhealthyConnectors.get(0), accountId);
    }
    return null;
  }

  private CCMConnectorDetails getConnectorDetails(ConnectorResponseDTO connector, String accountId) {
    if (connector != null) {
      ConnectorType connectorType = connector.getConnector().getConnectorType();
      AbstractCEConnectorValidator ceConnectorValidator = ceConnectorValidatorFactory.getValidator(connectorType);
      ConnectorValidationResult validationResult = null;
      if (ceConnectorValidator != null) {
        log.info("First connector dto {}", connector);
        validationResult = ceConnectorValidator.validate(connector, accountId);
      }

      return CCMConnectorDetails.builder()
          .name(connector.getConnector().getName())
          .createdAt(connector.getCreatedAt())
          .connectorValidationResult(validationResult)
          .build();
    } else {
      return null;
    }
  }
}
