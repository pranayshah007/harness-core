package io.harness.batch.processing.cloudevents.aws.ec2.service.tasklet;

import com.amazonaws.services.costexplorer.model.EC2ResourceDetails;
import com.amazonaws.services.costexplorer.model.EC2ResourceUtilization;
import com.amazonaws.services.costexplorer.model.RecommendationTarget;
import com.amazonaws.services.costexplorer.model.RightsizingRecommendation;
import com.google.inject.Singleton;
import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ec2.service.AWSEC2RecommendationService;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.AWSEC2Details;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.AWSRegionHelper;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.EC2MetricHelper;
import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationRequest;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.EC2RecommendationResponse;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.Ec2UtilzationData;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.MetricValue;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2RecommendationDetail;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.exception.InvalidRequestException;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static io.harness.batch.processing.ccm.UtilizationInstanceType.EC2_INSTANCE;
import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

@Slf4j
@Singleton
public class AWSEC2RecommendationTasklet  implements Tasklet {
    @Autowired private EC2MetricHelper ec2MetricHelper;
    @Autowired private AWSEC2RecommendationService awsec2RecommendationService;
    @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
    @Autowired private CECloudAccountDao ceCloudAccountDao;
    @Autowired private NGConnectorHelper ngConnectorHelper;
    @Autowired private UtilizationDataServiceImpl utilizationDataService;
    @Autowired private EC2RecommendationDAO ec2RecommendationDAO;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        log.info("Started EC2 recommendation job");
        final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
        String accountId = jobConstants.getAccountId();
        Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
        //TODO: Retrieve the recommendation data and store in db
        log.info("accountId = {}", accountId);
        // call aws get-metric-data to get the cpu utilisation data
        Map<String, AwsCrossAccountAttributes> infraAccCrossArnMap = getCrossAccountAttributes(accountId);
        log.info("infraAccCrossArnMap.size = {}", infraAccCrossArnMap.size());

        if (!infraAccCrossArnMap.isEmpty()) {
            for (Map.Entry<String, AwsCrossAccountAttributes> entry : infraAccCrossArnMap.entrySet()) {
                log.info("Key = {} Value = {}", entry.getKey(), entry.getValue());
                Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
                if (entry.getKey().equals("890436954479")) {

                    log.info("found the harness-ce account");
                    EC2RecommendationResponse ec2RecommendationResponse =
                            awsec2RecommendationService.getRecommendations(EC2RecommendationRequest.builder()
                                    .awsCrossAccountAttributes(entry.getValue())
                                    .build());
                    log.info("ec2RecommendationResponse = {}", ec2RecommendationResponse);

                    if (Objects.nonNull(ec2RecommendationResponse) &&
                            !ec2RecommendationResponse.getRecommendationMap().isEmpty()) {
                        log.info("recomm non null");
//                        List<String> instances = new ArrayList<>(Arrays.asList("i-0cf7994781dce538a", "i-0ee034ec9d9f456e8", "i-07bd941e66e9273c5", "i-054f2bed517243117"));
                        for (Map.Entry<RecommendationTarget, List<RightsizingRecommendation>> rightsizingRecommendations
                                : ec2RecommendationResponse.getRecommendationMap().entrySet()) {
                            log.info("map entry rightsizingRecommendations = {}", rightsizingRecommendations);
                            if (!rightsizingRecommendations.getValue().isEmpty()) {
                                rightsizingRecommendations.getValue().forEach(rightsizingRecommendation -> {
                                    log.info("list entry rightsizingRecommendation = {}", rightsizingRecommendation);
                                    EC2Recommendation ec2Recommendation = convertRecommendationObject(rightsizingRecommendation);
                                    ec2Recommendation.setAccountId(accountId);
                                    ec2Recommendation.setRecommendationType(rightsizingRecommendations.getKey().name());
                                    ec2Recommendation.setLastUpdatedTime(startTime);
                                    log.info("ec2Recommendation = {}", ec2Recommendation);
                                    ec2RecommendationDAO.saveRecommendation(ec2Recommendation);
                                    log.info("saved to mongo");
                                });
                            }
                        }

                        List<AWSEC2Details> instances = extractEC2InstanceDetails(ec2RecommendationResponse);
                        log.info("List<AWSEC2Details>.size = {}", instances.size());
                        log.info("instaceList = {}", instances);
                        List<Ec2UtilzationData> utilzationData =
                                ec2MetricHelper.getUtilizationMetrics(entry.getValue(), Date.from(now.minus(2, ChronoUnit.DAYS)),
                                        Date.from(now.minus(1, ChronoUnit.DAYS)), instances);
                        log.info("utilzationData.size = {}", utilzationData.size());
                        updateUtilData(accountId, utilzationData);
                    }

                    log.info("exiting the ec2 recomm!");
                }
            }
        }

        return null;
    }

    private void updateUtilData(String accountId, List<Ec2UtilzationData> utilizationMetricsList) {
        List<InstanceUtilizationData> instanceUtilizationDataList = new ArrayList<>();
        log.info("utilizationMetricsList size = {}", utilizationMetricsList.size());
        utilizationMetricsList.forEach(utilizationMetrics -> {
            String instanceId;
            String instanceType;
            instanceId = utilizationMetrics.getInstanceId();
            instanceType = EC2_INSTANCE;

            // Initialising List of Metrics to handle Utilization Metrics Downtime (Ideally this will be of size 1)
            // We do not need a Default value as such a scenario will never exist, if there is no data. It will not be
            // inserted to DB.
            long startTime = 0L;
            long oneHourMillis = Duration.ofDays(1).toMillis();
            List<Double> cpuUtilizationAvgList = new ArrayList<>();
            List<Double> cpuUtilizationMaxList = new ArrayList<>();
            List<Double> memoryUtilizationAvgList = new ArrayList<>();
            List<Double> memoryUtilizationMaxList = new ArrayList<>();
            List<Date> startTimestampList = new ArrayList<>();
            int metricsListSize = 0;

            for (MetricValue utilizationMetric : utilizationMetrics.getMetricValues()) {
                // Assumption that size of all the metrics and timestamps will be same across the 4 metrics
                if (!utilizationMetric.getTimestamps().isEmpty()) {
                    startTime = utilizationMetric.getTimestamps().get(0).toInstant().toEpochMilli();
                    log.info("startTime = {}", startTime);
                }

                List<Double> metricsList = utilizationMetric.getValues();
                metricsListSize = metricsList.size();
                log.info("metricsListSize = {}", metricsListSize);
                switch (utilizationMetric.getStatistic()) {
                    case "Maximum":
                        switch (utilizationMetric.getMetricName()) {
                            case "MemoryUtilization":
                                memoryUtilizationMaxList = metricsList;
                                break;
                            case "CPUUtilization":
                                cpuUtilizationMaxList = metricsList;
                                break;
                            default:
                                throw new InvalidRequestException("Invalid Utilization metric name");
                        }
                        break;
                    case "Average":
                        switch (utilizationMetric.getMetricName()) {
                            case "MemoryUtilization":
                                memoryUtilizationAvgList = metricsList;
                                break;
                            case "CPUUtilization":
                                cpuUtilizationAvgList = metricsList;
                                break;
                            default:
                                throw new InvalidRequestException("Invalid Utilization metric name");
                        }
                        break;
                    default:
                        throw new InvalidRequestException("Invalid Utilization metric Statistic");
                }
            }

            // POJO and insertion to DB

            InstanceUtilizationData utilizationData =
                    InstanceUtilizationData.builder()
                            .accountId(accountId)
                            .instanceId(instanceId)
                            .instanceType(instanceType)
                            .settingId(instanceId)
                            .clusterId(instanceId)
                            .cpuUtilizationMax((!cpuUtilizationMaxList.isEmpty()) ? getScaledUtilValue(cpuUtilizationMaxList.get(0)) : 0.0)
                            .cpuUtilizationAvg((!cpuUtilizationAvgList.isEmpty()) ?getScaledUtilValue(cpuUtilizationAvgList.get(0)) : 0.0)
                            .memoryUtilizationMax((!memoryUtilizationMaxList.isEmpty()) ?getScaledUtilValue(memoryUtilizationMaxList.get(0)) : 0.0)
                            .memoryUtilizationAvg((!memoryUtilizationAvgList.isEmpty()) ?getScaledUtilValue(memoryUtilizationAvgList.get(0)) : 0.0)
                            .startTimestamp(startTime)
                            .endTimestamp(startTime + oneHourMillis)
                            .build();
            log.info("utilizationData = {}", utilizationData);
            instanceUtilizationDataList.add(utilizationData);
        });

        log.info("size of the instanceUtilizationDataList lise = {}", instanceUtilizationDataList.size());

        utilizationDataService.create(instanceUtilizationDataList);
    }

    private double getScaledUtilValue(double value) {
        return value / 100;
    }

    private List<AWSEC2Details> extractEC2InstanceDetails(EC2RecommendationResponse response) {
        List<AWSEC2Details> awsec2Details = new ArrayList<>();
        for (Map.Entry<RecommendationTarget, List<RightsizingRecommendation>> rightsizingRecommendations
                : response.getRecommendationMap().entrySet()) {
            awsec2Details.addAll(rightsizingRecommendations.getValue().stream()
                    .map(rightsizingRecommendation -> {
                        String instanceId = rightsizingRecommendation.getCurrentInstance().getResourceId();
                        String region = rightsizingRecommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getRegion();
                        log.info("instanceId = {} region = {}", instanceId, region);
                        log.info("AWSRegionHelper.getRegionNameFromDisplayName(region) = {}",
                        AWSRegionHelper.getRegionNameFromDisplayName(region));
                        return new AWSEC2Details(instanceId, AWSRegionHelper.getRegionNameFromDisplayName(region));
            }).collect(Collectors.toList()));
        }
        return awsec2Details;
    }

    private EC2Recommendation convertRecommendationObject(RightsizingRecommendation recommendation) {
        return EC2Recommendation.builder()
                .awsAccountId(recommendation.getAccountId())
                .currentMaxCPU(recommendation.getCurrentInstance().getResourceUtilization().getEC2ResourceUtilization().getMaxCpuUtilizationPercentage())
                .currentMaxMemory(recommendation.getCurrentInstance().getResourceUtilization().getEC2ResourceUtilization().getMaxMemoryUtilizationPercentage())
                .currentMonthlyCost(recommendation.getCurrentInstance().getMonthlyCost())
                .currencyCode(recommendation.getCurrentInstance().getCurrencyCode())
                .instanceId(recommendation.getCurrentInstance().getResourceId())
                .instanceName(recommendation.getCurrentInstance().getInstanceName())
                .instanceType(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getInstanceType())
                .memory(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getMemory())
                .platform(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getPlatform())
                .region(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getRegion())
                .sku(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getSku())
                .vcpu(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getVcpu())
                .expectedMaxCPU(recommendation.getModifyRecommendationDetail().getTargetInstances().get(0).getExpectedResourceUtilization().getEC2ResourceUtilization().getMaxCpuUtilizationPercentage())
                .expectedMaxMemory(recommendation.getModifyRecommendationDetail().getTargetInstances().get(0).getExpectedResourceUtilization().getEC2ResourceUtilization().getMaxMemoryUtilizationPercentage())
                .recommendationInfo(buildRecommendationInfo(recommendation))
                .build();
    }

    private EC2RecommendationDetail buildRecommendationInfo(RightsizingRecommendation recommendation) {
        EC2ResourceDetails ec2ResourceDetails =
                recommendation.getModifyRecommendationDetail().getTargetInstances().get(0).getResourceDetails().getEC2ResourceDetails();
        return EC2RecommendationDetail.builder()
                .instanceType(ec2ResourceDetails.getInstanceType())
                .expectedMonthlyCost(recommendation.getModifyRecommendationDetail().getTargetInstances().get(0).getEstimatedMonthlyCost())
                .expectedMonthlySaving(recommendation.getModifyRecommendationDetail().getTargetInstances().get(0).getEstimatedMonthlySavings())
                .hourlyOnDemandRate(ec2ResourceDetails.getHourlyOnDemandRate())
                .memory(ec2ResourceDetails.getMemory())
                .platform(ec2ResourceDetails.getPlatform())
                .region(ec2ResourceDetails.getRegion())
                .sku(ec2ResourceDetails.getSku())
                .vcpu(ec2ResourceDetails.getVcpu())
                .build();
    }

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
