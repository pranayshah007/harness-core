package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.aws;

import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;

import software.wings.beans.AwsCrossAccountAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CEAWSConfigHelper {
  @Autowired private NGConnectorHelper ngConnectorHelper;

  public Map<String, AwsCrossAccountAttributes> getCrossAccountAttributes(String accountId) {
    Map<String, AwsCrossAccountAttributes> crossAccountAttributesMap = new HashMap<>();
    List<ConnectorResponseDTO> nextGenConnectors =
        ngConnectorHelper.getNextGenConnectors(accountId, Arrays.asList(ConnectorType.CE_AWS),
            Arrays.asList(CEFeatures.VISIBILITY), Arrays.asList(ConnectivityStatus.SUCCESS));
    for (ConnectorResponseDTO connector : nextGenConnectors) {
      ConnectorInfoDTO connectorInfo = connector.getConnector();
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
      if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
        AwsCrossAccountAttributes crossAccountAttributes =
            AwsCrossAccountAttributes.builder()
                .crossAccountRoleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                .build();
        crossAccountAttributesMap.put(ceAwsConnectorDTO.getAwsAccountId(), crossAccountAttributes);
      }
    }
    return crossAccountAttributesMap;
  }
}
