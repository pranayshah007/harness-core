package io.harness.batch.processing.cloudevents.aws.ec2.service.tasklet;

import com.google.inject.Singleton;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.EC2MetricHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

@Slf4j
@Singleton
public class AWSEC2RecommendationTasklet  implements Tasklet {
    @Autowired private EC2MetricHelper ec2MetricHelper;
    @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
    @Autowired private CECloudAccountDao ceCloudAccountDao;
    @Autowired private NGConnectorHelper ngConnectorHelper;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        log.info("Started EC2 recommendation data");
        final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
        String accountId = jobConstants.getAccountId();
        Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
        //TODO: Retrieve the recommendation data and store in db
        log.info("accountId = {}", accountId);
        // call aws get-metric-data to get the cpu utilisation data
        Map<String, AwsCrossAccountAttributes> infraAccCrossArnMap = getCrossAccountAttributes(accountId);
        log.info("infraAccCrossArnMap.size = {}", infraAccCrossArnMap.size());

        for (Map.Entry<String, AwsCrossAccountAttributes> entry : infraAccCrossArnMap.entrySet()) {
            log.info("Key = {} Value = {}", entry.getKey(), entry.getValue());
        }

//        AwsCrossAccountAttributes awsCrossArn = infraAccCrossArnMap.get();

        return null;
    }

//    private syncEC2UtilizationData(String instanceId, String accountId) {
//
//        ec2MetricHelper.getUtilizationMetrics(instanceId);
//    }

    private Map<String, AwsCrossAccountAttributes> getCrossAccountAttributes(String accountId) {
        List<SettingAttribute> ceConnectorsList =
                cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);
        Map<String, AwsCrossAccountAttributes> crossAccountAttributesMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(ceConnectorsList)) {
            List<CECloudAccount> ceCloudAccountList =
                    ceCloudAccountDao.getBySettingId(accountId, ceConnectorsList.get(0).getUuid());
            ceCloudAccountList.forEach(ceCloudAccount
                    -> crossAccountAttributesMap.put(
                    ceCloudAccount.getInfraAccountId(), ceCloudAccount.getAwsCrossAccountAttributes()));
            List<SettingAttribute> ceConnectorList =
                    cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);
            ceConnectorList.forEach(ceConnector -> {
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
