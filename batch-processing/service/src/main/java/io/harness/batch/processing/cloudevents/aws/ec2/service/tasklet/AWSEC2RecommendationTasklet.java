/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ec2.service.tasklet;

import static io.harness.batch.processing.ccm.UtilizationInstanceType.EC2_INSTANCE;

import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ec2.service.AWSEC2RecommendationService;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.AWSEC2Details;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.AWSRegionRegistry;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.EC2InstanceRecommendationInfo;
import io.harness.batch.processing.cloudevents.aws.ec2.service.helper.EC2MetricHelper;
import io.harness.batch.processing.cloudevents.aws.ec2.service.request.EC2RecommendationRequest;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.EC2RecommendationResponse;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.Ec2UtilzationData;
import io.harness.batch.processing.cloudevents.aws.ec2.service.response.MetricValue;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.aws.CEAWSConfigHelper;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.dao.recommendation.EC2RecommendationDAO;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2RecommendationDetail;
import io.harness.ccm.graphql.core.recommendation.RecommendationsIgnoreListService;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.amazonaws.services.costexplorer.model.EC2ResourceDetails;
import com.amazonaws.services.costexplorer.model.RecommendationTarget;
import com.amazonaws.services.costexplorer.model.RightsizingRecommendation;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class AWSEC2RecommendationTasklet implements Tasklet {
  @Autowired private EC2MetricHelper ec2MetricHelper;
  @Autowired private AWSEC2RecommendationService awsEc2RecommendationService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  @Autowired private EC2RecommendationDAO ec2RecommendationDAO;
  @Autowired private CEAWSConfigHelper ceawsConfigHelper;
  @Autowired private RecommendationsIgnoreListService ignoreListService;
  @Autowired private AwsAccountFieldHelper awsAccountFieldHelper;

  private static final String MODIFY = "Modify";
  private static final int METRICS_DATA_BATCH_SIZE = 20;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    log.info("Running the EC2 recommendation job");
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    // call aws get-metric-data to get the cpu & memory utilisation data
    Map<String, AwsCrossAccountAttributes> infraAccCrossArnMap = ceawsConfigHelper.getCrossAccountAttributes(accountId);

    if (!infraAccCrossArnMap.isEmpty()) {
      for (Map.Entry<String, AwsCrossAccountAttributes> infraAccCrossArn : infraAccCrossArnMap.entrySet()) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);
        // fetching the aws ec2 recommendations
        EC2RecommendationResponse ec2RecommendationResponse = awsEc2RecommendationService.getRecommendations(
            EC2RecommendationRequest.builder().awsCrossAccountAttributes(infraAccCrossArn.getValue()).build());
        Map<String, List<EC2InstanceRecommendationInfo>> instanceLevelRecommendations = new HashMap<>();
        log.info("Ec2RecommendationResponse = {}", ec2RecommendationResponse);

        if (Objects.nonNull(ec2RecommendationResponse) && !ec2RecommendationResponse.getRecommendationMap().isEmpty()) {
          for (Map.Entry<RecommendationTarget, List<RightsizingRecommendation>> rightsizingRecommendations :
              ec2RecommendationResponse.getRecommendationMap().entrySet()) {
            if (!rightsizingRecommendations.getValue().isEmpty()) {
              rightsizingRecommendations.getValue().forEach(rightsizingRecommendation -> {
                instanceLevelRecommendations.putIfAbsent(
                    rightsizingRecommendation.getCurrentInstance().getResourceId(), new ArrayList<>());
                instanceLevelRecommendations.get(rightsizingRecommendation.getCurrentInstance().getResourceId())
                    .add(new EC2InstanceRecommendationInfo(
                        rightsizingRecommendations.getKey().name(), rightsizingRecommendation));
              });
            }
          }
          for (Map.Entry<String, List<EC2InstanceRecommendationInfo>> instanceLevelRecommendation :
              instanceLevelRecommendations.entrySet()) {
            if (!instanceLevelRecommendation.getValue().isEmpty()) {
              EC2Recommendation recommendation;
              if (instanceLevelRecommendation.getValue()
                      .get(0)
                      .getRecommendation()
                      .getRightsizingType()
                      .equalsIgnoreCase(MODIFY)) {
                recommendation = buildRecommendationObjectFromModifyType(instanceLevelRecommendation.getValue());
              } else {
                recommendation = buildRecommendationForTerminationType(instanceLevelRecommendation.getValue());
              }
              recommendation.setAccountId(accountId);
              recommendation.setLastUpdatedTime(startTime);
              // Save the ec2 recommendation to mongo and timescale
              log.info("Saving ec2Recommendation: {}", recommendation);
              try {
                EC2Recommendation ec2Recommendation = ec2RecommendationDAO.saveRecommendation(recommendation);
                log.info("EC2Recommendation saved to mongoDB = {}", ec2Recommendation);
                saveRecommendationInTimeScaleDB(ec2Recommendation);
                ignoreListService.updateEC2RecommendationState(ec2Recommendation.getUuid(), accountId,
                    ec2Recommendation.getAwsAccountId(), ec2Recommendation.getInstanceId());
              } catch (Exception e) {
                log.error("Couldn't save recommendation: {}", recommendation, e);
              }
            }
          }
          List<AWSEC2Details> instances = extractEC2InstanceDetails(ec2RecommendationResponse);
          // We do this in batches because AWS's getMetricData API throws error if the list is too huge
          for (List<AWSEC2Details> instancesPartition : Lists.partition(instances, METRICS_DATA_BATCH_SIZE)) {
            List<Ec2UtilzationData> utilizationData = ec2MetricHelper.getUtilizationMetrics(infraAccCrossArn.getValue(),
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now), instancesPartition);
            if (!utilizationData.isEmpty()) {
              saveUtilDataToTimescaleDB(accountId, utilizationData);
            }
          }
        }
      }
    }

    return null;
  }

  private void saveUtilDataToTimescaleDB(String accountId, List<Ec2UtilzationData> utilizationMetricsList) {
    List<InstanceUtilizationData> instanceUtilizationDataList = new ArrayList<>();
    utilizationMetricsList.forEach(utilizationMetrics -> {
      String instanceId;
      String instanceType;
      instanceId = utilizationMetrics.getInstanceId();
      instanceType = EC2_INSTANCE;

      long startTime = 0L;
      long oneDayMillis = Duration.ofDays(1).toMillis();
      boolean utilDataPresent = false;
      List<Double> cpuUtilizationAvgList = new ArrayList<>();
      List<Double> cpuUtilizationMaxList = new ArrayList<>();
      List<Double> memoryUtilizationAvgList = new ArrayList<>();
      List<Double> memoryUtilizationMaxList = new ArrayList<>();

      for (MetricValue utilizationMetric : utilizationMetrics.getMetricValues()) {
        if (!utilizationMetric.getTimestamps().isEmpty()) {
          startTime = utilizationMetric.getTimestamps().get(0).toInstant().toEpochMilli();
        }

        List<Double> metricsList = utilizationMetric.getValues();
        switch (utilizationMetric.getStatistic()) {
          case "Maximum":
            switch (utilizationMetric.getMetricName()) {
              case "MemoryUtilization":
                memoryUtilizationMaxList = metricsList;
                utilDataPresent = true;
                break;
              case "CPUUtilization":
                cpuUtilizationMaxList = metricsList;
                utilDataPresent = true;
                break;
              default:
                throw new InvalidRequestException("Invalid Utilization metric name");
            }
            break;
          case "Average":
            switch (utilizationMetric.getMetricName()) {
              case "MemoryUtilization":
                memoryUtilizationAvgList = metricsList;
                utilDataPresent = true;
                break;
              case "CPUUtilization":
                cpuUtilizationAvgList = metricsList;
                utilDataPresent = true;
                break;
              default:
                throw new InvalidRequestException("Invalid Utilization metric name");
            }
            break;
          default:
            throw new InvalidRequestException("Invalid Utilization metric Statistic");
        }
      }

      InstanceUtilizationData utilizationData =
          InstanceUtilizationData.builder()
              .accountId(accountId)
              .instanceId(instanceId)
              .instanceType(instanceType)
              .settingId(instanceId)
              .clusterId(instanceId)
              .cpuUtilizationMax((!cpuUtilizationMaxList.isEmpty()) ? cpuUtilizationMaxList.get(0) : 0.0)
              .cpuUtilizationAvg((!cpuUtilizationAvgList.isEmpty()) ? cpuUtilizationAvgList.get(0) : 0.0)
              .memoryUtilizationMax((!memoryUtilizationMaxList.isEmpty()) ? memoryUtilizationMaxList.get(0) : 0.0)
              .memoryUtilizationAvg((!memoryUtilizationAvgList.isEmpty()) ? memoryUtilizationAvgList.get(0) : 0.0)
              .startTimestamp(startTime)
              .endTimestamp(startTime + oneDayMillis)
              .build();
      if (utilDataPresent) {
        instanceUtilizationDataList.add(utilizationData);
      }
    });

    if (!instanceUtilizationDataList.isEmpty()) {
      utilizationDataService.create(instanceUtilizationDataList);
    }
  }

  private List<AWSEC2Details> extractEC2InstanceDetails(EC2RecommendationResponse response) {
    List<AWSEC2Details> awsEC2Details = new ArrayList<>();
    for (Map.Entry<RecommendationTarget, List<RightsizingRecommendation>> rightsizingRecommendations :
        response.getRecommendationMap().entrySet()) {
      awsEC2Details.addAll(rightsizingRecommendations.getValue()
                               .stream()
                               .map(rightsizingRecommendation -> {
                                 String instanceId = rightsizingRecommendation.getCurrentInstance().getResourceId();
                                 String region = rightsizingRecommendation.getCurrentInstance()
                                                     .getResourceDetails()
                                                     .getEC2ResourceDetails()
                                                     .getRegion();
                                 return new AWSEC2Details(
                                     instanceId, AWSRegionRegistry.getRegionNameFromDisplayName(region));
                               })
                               .collect(Collectors.toList()));
    }
    return awsEC2Details;
  }

  private EC2Recommendation buildRecommendationObjectFromModifyType(
      List<EC2InstanceRecommendationInfo> recommendations) {
    RightsizingRecommendation recommendation = recommendations.get(0).getRecommendation();
    return EC2Recommendation.builder()
        .awsAccountId(recommendation.getAccountId())
        .currentMaxCPU(recommendation.getCurrentInstance()
                           .getResourceUtilization()
                           .getEC2ResourceUtilization()
                           .getMaxCpuUtilizationPercentage())
        .currentMaxMemory(recommendation.getCurrentInstance()
                              .getResourceUtilization()
                              .getEC2ResourceUtilization()
                              .getMaxMemoryUtilizationPercentage())
        .currentMonthlyCost(recommendation.getCurrentInstance().getMonthlyCost())
        .currencyCode(recommendation.getCurrentInstance().getCurrencyCode())
        .instanceId(recommendation.getCurrentInstance().getResourceId())
        .instanceName(recommendation.getCurrentInstance().getInstanceName())
        .instanceType(
            recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getInstanceType())
        .memory(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getMemory())
        .platform(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getPlatform())
        .region(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getRegion())
        .sku(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getSku())
        .vcpu(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getVcpu())
        .recommendationInfo(buildRecommendationInfo(recommendations))
        .expectedSaving(calculateMaxSaving(recommendations))
        .rightsizingType(recommendation.getRightsizingType())
        .build();
  }

  private EC2Recommendation buildRecommendationForTerminationType(List<EC2InstanceRecommendationInfo> recommendations) {
    RightsizingRecommendation recommendation = recommendations.get(0).getRecommendation();
    return EC2Recommendation.builder()
        .awsAccountId(recommendation.getAccountId())
        .currentMaxCPU(recommendation.getCurrentInstance()
                           .getResourceUtilization()
                           .getEC2ResourceUtilization()
                           .getMaxCpuUtilizationPercentage())
        .currentMaxMemory(recommendation.getCurrentInstance()
                              .getResourceUtilization()
                              .getEC2ResourceUtilization()
                              .getMaxMemoryUtilizationPercentage())
        .currentMonthlyCost(recommendation.getCurrentInstance().getMonthlyCost())
        .currencyCode(recommendation.getCurrentInstance().getCurrencyCode())
        .instanceId(recommendation.getCurrentInstance().getResourceId())
        .instanceName(recommendation.getCurrentInstance().getInstanceName())
        .instanceType(
            recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getInstanceType())
        .memory(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getMemory())
        .platform(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getPlatform())
        .region(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getRegion())
        .sku(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getSku())
        .vcpu(recommendation.getCurrentInstance().getResourceDetails().getEC2ResourceDetails().getVcpu())
        .expectedSaving(recommendation.getTerminateRecommendationDetail().getEstimatedMonthlySavings())
        .rightsizingType(recommendation.getRightsizingType())
        .build();
  }

  private List<EC2RecommendationDetail> buildRecommendationInfo(List<EC2InstanceRecommendationInfo> recommendations) {
    return recommendations.stream()
        .map(recommendation -> {
          EC2ResourceDetails ec2ResourceDetails = recommendation.getRecommendation()
                                                      .getModifyRecommendationDetail()
                                                      .getTargetInstances()
                                                      .get(0)
                                                      .getResourceDetails()
                                                      .getEC2ResourceDetails();
          return EC2RecommendationDetail.builder()
              .instanceType(ec2ResourceDetails.getInstanceType())
              .hourlyOnDemandRate(ec2ResourceDetails.getHourlyOnDemandRate())
              .memory(ec2ResourceDetails.getMemory())
              .platform(ec2ResourceDetails.getPlatform())
              .region(ec2ResourceDetails.getRegion())
              .sku(ec2ResourceDetails.getSku())
              .vcpu(ec2ResourceDetails.getVcpu())
              .recommendationType(recommendation.getRecommendationType())
              .expectedMonthlySaving(recommendation.getRecommendation()
                                         .getModifyRecommendationDetail()
                                         .getTargetInstances()
                                         .get(0)
                                         .getEstimatedMonthlySavings())
              .expectedMonthlyCost(recommendation.getRecommendation()
                                       .getModifyRecommendationDetail()
                                       .getTargetInstances()
                                       .get(0)
                                       .getEstimatedMonthlyCost())
              .expectedMaxCPU(recommendation.getRecommendation()
                                  .getModifyRecommendationDetail()
                                  .getTargetInstances()
                                  .get(0)
                                  .getExpectedResourceUtilization()
                                  .getEC2ResourceUtilization()
                                  .getMaxCpuUtilizationPercentage())
              .expectedMaxMemory(recommendation.getRecommendation()
                                     .getModifyRecommendationDetail()
                                     .getTargetInstances()
                                     .get(0)
                                     .getExpectedResourceUtilization()
                                     .getEC2ResourceUtilization()
                                     .getMaxMemoryUtilizationPercentage())
              .build();
        })
        .collect(Collectors.toList());
  }

  private void saveRecommendationInTimeScaleDB(EC2Recommendation ec2Recommendation) {
    Double currentMonthCost = Double.parseDouble(
        ec2Recommendation.getCurrentMonthlyCost().isEmpty() ? "0.0" : ec2Recommendation.getCurrentMonthlyCost());
    Double monthlySaving = Double.parseDouble(
        ec2Recommendation.getExpectedSaving().isEmpty() ? "0.0" : ec2Recommendation.getExpectedSaving());
    List<String> nameSpace = awsAccountFieldHelper.mergeAwsAccountNameWithValues(
        Collections.singletonList(ec2Recommendation.getAwsAccountId()), ec2Recommendation.getAccountId());
    String name = ec2Recommendation.getInstanceType() + " (" + ec2Recommendation.getInstanceId() + ")";
    ec2RecommendationDAO.upsertCeRecommendation(ec2Recommendation.getUuid(), ec2Recommendation.getAccountId(),
        ec2Recommendation.getInstanceId(), nameSpace.get(0), name, currentMonthCost, monthlySaving,
        ec2Recommendation.getLastUpdatedTime());
  }

  private String calculateMaxSaving(List<EC2InstanceRecommendationInfo> recommendations) {
    Double maxCost = recommendations.stream()
                         .map(rightsizingRecommendation
                             -> Double.valueOf(rightsizingRecommendation.getRecommendation()
                                                   .getModifyRecommendationDetail()
                                                   .getTargetInstances()
                                                   .get(0)
                                                   .getEstimatedMonthlySavings()))
                         .reduce(Double::max)
                         .orElse(0.0);
    return String.valueOf(maxCost);
  }
}
