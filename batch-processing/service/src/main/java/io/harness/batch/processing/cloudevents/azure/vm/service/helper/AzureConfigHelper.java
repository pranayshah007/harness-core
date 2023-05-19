/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.azure.vm.service.helper;

import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;

import software.wings.beans.AzureAccountAttributes;

import io.fabric8.utils.Lists;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AzureConfigHelper {
  @Autowired private NGConnectorHelper ngConnectorHelper;

  public Map<String, AzureAccountAttributes> getAzureAccountAttributes(String accountId) {
    Map<String, AzureAccountAttributes> azureAccountAttributesMap = new HashMap<>();
    List<ConnectorResponseDTO> nextGenConnectors = ngConnectorHelper.getNextGenConnectors(accountId,
        Arrays.asList(ConnectorType.CE_AZURE), Arrays.asList(CEFeatures.VISIBILITY, CEFeatures.BILLING),
        Arrays.asList(ConnectivityStatus.SUCCESS, ConnectivityStatus.FAILURE, ConnectivityStatus.PARTIAL,
            ConnectivityStatus.UNKNOWN));
    for (ConnectorResponseDTO connector : nextGenConnectors) {
      ConnectorInfoDTO connectorInfo = connector.getConnector();
      CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorInfo.getConnectorConfig();
      if (visibilityAndInventoryNotEnabled(ceAzureConnectorDTO.getFeaturesEnabled())) {
        continue;
      }
      if (ceAzureConnectorDTO != null) {
        AzureAccountAttributes azureAccountAttributes = AzureAccountAttributes.builder()
                                                            .connectorId(connectorInfo.getIdentifier())
                                                            .connectorName(connectorInfo.getName())
                                                            .tenantId(ceAzureConnectorDTO.getTenantId())
                                                            .subscriptionId(ceAzureConnectorDTO.getSubscriptionId())
                                                            .build();
        azureAccountAttributesMap.put(
            ceAzureConnectorDTO.getTenantId() + "-" + ceAzureConnectorDTO.getSubscriptionId(), azureAccountAttributes);
      }
    }
    return azureAccountAttributesMap;
  }

  private boolean visibilityAndInventoryNotEnabled(List<CEFeatures> ceFeaturesList) {
    if (Lists.isNullOrEmpty(ceFeaturesList)) {
      return true;
    }
    return !ceFeaturesList.contains(CEFeatures.VISIBILITY) || !ceFeaturesList.contains(CEFeatures.BILLING);
  }
}
