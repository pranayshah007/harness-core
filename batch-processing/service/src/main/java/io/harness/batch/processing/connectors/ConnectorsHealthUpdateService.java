/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.connectors;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.RestCallToNGManagerClientUtils;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class ConnectorsHealthUpdateService {
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private BatchMainConfig mainConfig;
  @Autowired private AccountShardService accountShardService;

  public void update() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    log.info("accounts size: {}", accountIds.size());
    for (String accountId : accountIds) {
      log.info("Fetching connectors for  account id {}", accountId);
      List<ConnectorResponseDTO> nextGenConnectorResponses = getNextGenConnectorResponses(accountId);
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        try {
          processConnector(connector, accountId);
        } catch (Exception e) {
          log.error(
              "Exception processing Connector id: {} for account id: {}", connectorInfo.getIdentifier(), accountId, e);
        }
      }
    }
  }

  public void processConnector(ConnectorResponseDTO connector, String accountId) {
    log.info("connector.getConnector().getIdentifier(): {}, accountId: {}", connector.getConnector().getIdentifier(),
        accountId);
    ConnectorValidationResult connectorValidationResult =
        RestCallToNGManagerClientUtils.execute(connectorResourceClient.testConnectionInternal(
            connector.getConnector().getIdentifier(), accountId, null, null));
    log.info("connectorValidationResult {}", connectorValidationResult);
  }

  public List<ConnectorResponseDTO> getNextGenConnectorResponses(String accountId) {
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .types(Arrays.asList(ConnectorType.GCP_CLOUD_COST, ConnectorType.CE_AWS, ConnectorType.CE_AZURE))
            .ccmConnectorFilter(CcmConnectorFilter.builder()
                                    .featuresEnabled(Arrays.asList(CEFeatures.BILLING, CEFeatures.OPTIMIZATION))
                                    .build())
            //.connectivityStatuses(Arrays.asList(ConnectivityStatus.SUCCESS))
            .build();
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
    int page = 0;
    int size = 100;
    do {
      response = getConnectors(accountId, page, size, connectorFilterPropertiesDTO);
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectorResponses.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));
    log.info("Processing batch size of {}", nextGenConnectorResponses.size());
    return nextGenConnectorResponses;
  }

  PageResponse getConnectors(
      String accountId, int page, int size, ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO) {
    return RestCallToNGManagerClientUtils.execute(
        connectorResourceClient.listConnectors(accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
  }
}
