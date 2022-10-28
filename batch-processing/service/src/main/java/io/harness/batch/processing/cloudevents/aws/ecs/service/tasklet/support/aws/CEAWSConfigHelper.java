package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.aws;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Slf4j
public class CEAWSConfigHelper {
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private CECloudAccountDao ceCloudAccountDao;
  @Autowired private NGConnectorHelper ngConnectorHelper;

  public Map<String, AwsCrossAccountAttributes> getCrossAccountAttributes(String accountId) {
    List<SettingAttribute> ceConnectorsList =
        cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);
    Map<String, AwsCrossAccountAttributes> crossAccountAttributesMap = new HashMap<>();
    if (!CollectionUtils.isEmpty(ceConnectorsList)) {
      List<CECloudAccount> ceCloudAccountList =
          ceCloudAccountDao.getBySettingId(accountId, ceConnectorsList.get(0).getUuid());
      ceCloudAccountList.forEach(ceCloudAccount
          -> crossAccountAttributesMap.put(
              ceCloudAccount.getInfraAccountId(), ceCloudAccount.getAwsCrossAccountAttributes()));
      ceConnectorsList.forEach(ceConnector -> {
        CEAwsConfig ceAwsConfig = (CEAwsConfig) ceConnector.getValue();
        crossAccountAttributesMap.put(ceAwsConfig.getAwsMasterAccountId(), ceAwsConfig.getAwsCrossAccountAttributes());
      });
    }
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
