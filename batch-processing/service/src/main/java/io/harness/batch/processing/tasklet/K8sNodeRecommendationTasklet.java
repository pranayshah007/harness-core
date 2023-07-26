/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.pricing.banzai.BanzaiRecommenderClient;
import io.harness.batch.processing.pricing.vmpricing.VMPricingService;
import io.harness.batch.processing.tasklet.util.ClusterHelper;
import io.harness.batch.processing.tasklet.util.CurrencyPreferenceHelper;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.beans.recommendation.K8sServiceProvider;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.RecommendationUtils;
import io.harness.ccm.commons.beans.recommendation.TotalResourceUsage;
import io.harness.ccm.commons.beans.recommendation.models.ClusterRecommendationAccuracy;
import io.harness.ccm.commons.beans.recommendation.models.ErrorResponse;
import io.harness.ccm.commons.beans.recommendation.models.NodePool;
import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.dao.recommendation.RecommendationCrudService;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.graphql.core.recommendation.RecommendationsIgnoreListService;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;
import io.harness.exception.InvalidRequestException;
import io.harness.pricing.dto.cloudinfo.ProductDetails;

import com.google.gson.Gson;
import io.fabric8.utils.Lists;
import java.net.ConnectException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;

@Slf4j
@OwnedBy(CE)
public class K8sNodeRecommendationTasklet implements Tasklet {
  @Autowired private K8sRecommendationDAO k8sRecommendationDAO;
  @Autowired private RecommendationCrudService recommendationCrudService;
  @Autowired private BanzaiRecommenderClient banzaiRecommenderClient;
  @Autowired private VMPricingService vmPricingService;
  @Autowired private ClusterHelper clusterHelper;
  @Autowired private CurrencyPreferenceHelper currencyPreferenceHelper;
  @Autowired private RecommendationsIgnoreListService ignoreListService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();

    List<NodePoolId> nodePoolIdList = k8sRecommendationDAO.getUniqueNodePools(accountId);

    for (NodePoolId nodePoolId : nodePoolIdList) {
      if (nodePoolId.getNodepoolname() == null) {
        log.info("There is a node with node_pool_name as null in [accountId:{}, clusterId:{}], skipping",
            jobConstants.getAccountId(), nodePoolId.getClusterid());
        continue;
      }
      if (k8sRecommendationDAO.fetchDistinctInstanceFamilies(jobConstants, nodePoolId).size() > 1) {
        log.info("There is a node_pool with multiple instance families in [accountId:{}, nodePoolId:{}], skipping",
            jobConstants.getAccountId(), nodePoolId);
        continue;
      }

      createTotalResourceUsageAndInsert(jobConstants, nodePoolId);

      OffsetDateTime startTime = toOffsetDateTime(jobConstants.getJobStartTime()).minusDays(6);
      OffsetDateTime endTime = toOffsetDateTime(jobConstants.getJobEndTime());
      JobConstants jobConstants7Days = JobConstants.builder()
                                           .accountId(accountId)
                                           .jobStartTime(startTime.toEpochSecond() * 1000)
                                           .jobEndTime(endTime.toEpochSecond() * 1000)
                                           .build();
      TotalResourceUsage totalResourceUsage =
          k8sRecommendationDAO.aggregateTotalResourceRequirement(accountId, nodePoolId, startTime, endTime);
      logTotalResourceUsage(jobConstants7Days, nodePoolId, totalResourceUsage);

      try {
        calculateAndSaveRecommendation(jobConstants7Days, nodePoolId, totalResourceUsage);
      } catch (InvalidRequestException
              ex) { // This an acceptable exception in the sense that the next recommendation job should proceed.
        log.warn("Not generating recommendation for: {} with TRU: {}", nodePoolId, totalResourceUsage, ex);
      }
    }

    return null;
  }

  private void createTotalResourceUsageAndInsert(@NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId) {
    TotalResourceUsage totalResourceUsage =
        k8sRecommendationDAO.maxResourceOfAllTimeBucketsForANodePool(jobConstants, nodePoolId);
    logTotalResourceUsage(jobConstants, nodePoolId, totalResourceUsage);
    k8sRecommendationDAO.insertNodePoolAggregated(jobConstants, nodePoolId, totalResourceUsage);
  }

  private void logTotalResourceUsage(@NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId,
      @NonNull TotalResourceUsage totalResourceUsage) {
    log.info("TotalResourceUsage for {}:{} is {} between {} and {}", jobConstants.getAccountId(), nodePoolId,
        totalResourceUsage, toOffsetDateTime(jobConstants.getJobStartTime()),
        toOffsetDateTime(jobConstants.getJobEndTime()));
  }

  private void calculateAndSaveRecommendation(@NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId,
      @NonNull TotalResourceUsage totalResourceUsage) {
    K8sServiceProvider serviceProvider = getCurrentNodePoolConfiguration(jobConstants, nodePoolId);
    log.info("serviceProvider: {}", serviceProvider);

    final Double conversionFactor = currencyPreferenceHelper.getDestinationCurrencyConversionFactor(
        jobConstants.getAccountId(), getCloudServiceProvider(serviceProvider), Currency.USD);

    updateK8sServiceProvider(serviceProvider, conversionFactor);
    log.info("Updated serviceProvider: {}", serviceProvider);

    RecommendClusterRequest request = RecommendationUtils.constructNodeRecommendationRequest(totalResourceUsage);
    log.info("RecommendClusterRequest: {}", request);

    RecommendationResponse recommendation = getRecommendation(serviceProvider, request);
    log.info("RecommendationResponse: {}", recommendation);

    updateRecommendationResponse(recommendation, conversionFactor);
    log.info("Updated RecommendationResponse: {}", recommendation);

    String mongoEntityId = k8sRecommendationDAO.insertNodeRecommendationResponse(
        jobConstants, nodePoolId, request, serviceProvider, recommendation, totalResourceUsage);

    RecommendationOverviewStats stats = getMonthlyCostAndSaving(serviceProvider, recommendation);
    log.info("The monthly stat is: {}", stats);

    final String clusterName = clusterHelper.fetchClusterName(nodePoolId.getClusterid());
    recommendationCrudService.upsertNodeRecommendation(
        mongoEntityId, jobConstants, nodePoolId, clusterName, stats, serviceProvider.getCloudProvider().name());
    ignoreListService.updateNodeRecommendationState(
        mongoEntityId, jobConstants.getAccountId(), clusterName, nodePoolId.getNodepoolname());
  }

  private void updateK8sServiceProvider(@NonNull K8sServiceProvider serviceProvider, @NonNull Double conversionFactor) {
    serviceProvider.setCostPerVmPerHr(serviceProvider.getCostPerVmPerHr() * conversionFactor);
    serviceProvider.setSpotCostPerVmPerHr(serviceProvider.getSpotCostPerVmPerHr() * conversionFactor);
  }

  private void updateRecommendationResponse(
      @NonNull RecommendationResponse recommendation, @NonNull Double conversionFactor) {
    updateAccuracyPrices(recommendation.getAccuracy(), conversionFactor);
    updateVMPrices(recommendation.getNodePools(), conversionFactor);
  }

  private void updateAccuracyPrices(
      ClusterRecommendationAccuracy clusterRecommendationAccuracy, @NonNull Double conversionFactor) {
    if (Objects.nonNull(clusterRecommendationAccuracy)) {
      if (Objects.nonNull(clusterRecommendationAccuracy.getSpotPrice())) {
        clusterRecommendationAccuracy.setSpotPrice(clusterRecommendationAccuracy.getSpotPrice() * conversionFactor);
      }
      if (Objects.nonNull(clusterRecommendationAccuracy.getWorkerPrice())) {
        clusterRecommendationAccuracy.setWorkerPrice(clusterRecommendationAccuracy.getWorkerPrice() * conversionFactor);
      }
      if (Objects.nonNull(clusterRecommendationAccuracy.getRegularPrice())) {
        clusterRecommendationAccuracy.setRegularPrice(
            clusterRecommendationAccuracy.getRegularPrice() * conversionFactor);
      }
      if (Objects.nonNull(clusterRecommendationAccuracy.getMasterPrice())) {
        clusterRecommendationAccuracy.setMasterPrice(clusterRecommendationAccuracy.getMasterPrice() * conversionFactor);
      }
      if (Objects.nonNull(clusterRecommendationAccuracy.getTotalPrice())) {
        clusterRecommendationAccuracy.setTotalPrice(clusterRecommendationAccuracy.getTotalPrice() * conversionFactor);
      }
    }
  }

  private void updateVMPrices(List<NodePool> nodePools, @NonNull Double conversionFactor) {
    if (!Lists.isNullOrEmpty(nodePools)) {
      for (NodePool nodePool : nodePools) {
        if (Objects.nonNull(nodePool.getVm())) {
          if (Objects.nonNull(nodePool.getVm().getAvgPrice())) {
            nodePool.getVm().setAvgPrice(nodePool.getVm().getAvgPrice() * conversionFactor);
          }
          if (Objects.nonNull(nodePool.getVm().getOnDemandPrice())) {
            nodePool.getVm().setOnDemandPrice(nodePool.getVm().getOnDemandPrice() * conversionFactor);
          }
        }
      }
    }
  }

  @NotNull
  private CloudServiceProvider getCloudServiceProvider(K8sServiceProvider serviceProvider) {
    CloudServiceProvider cloudServiceProvider;
    try {
      cloudServiceProvider = CloudServiceProvider.valueOf(serviceProvider.getCloudProvider().name());
    } catch (Exception exception) {
      cloudServiceProvider = CloudServiceProvider.AWS;
    }
    return cloudServiceProvider;
  }

  private K8sServiceProvider getCurrentNodePoolConfiguration(
      @NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId) {
    K8sServiceProvider serviceProvider = k8sRecommendationDAO.getServiceProvider(jobConstants, nodePoolId);

    populateVMInfo(serviceProvider);

    return serviceProvider;
  }

  @SneakyThrows
  private RecommendationResponse getRecommendation(
      K8sServiceProvider serviceProvider, RecommendClusterRequest request) {
    Response<RecommendationResponse> response =
        banzaiRecommenderClient
            .getRecommendation(serviceProvider.getCloudProvider().getCloudProviderName(),
                serviceProvider.getCloudProvider().getK8sService(), serviceProvider.getRegion(), request)
            .execute();

    if (!response.isSuccessful()) {
      log.error("banzaiRecommenderClient response: {}", response);
      String exMessage = "Failed to get/parse response from banzai recommender";

      if (response.code() == 400) {
        Gson gson = new Gson();
        ErrorResponse errorResponse = gson.fromJson(response.errorBody().charStream(), ErrorResponse.class);
        exMessage = errorResponse.getDetail();

        if ("No node pool could be recommended with the specified tuning parameters".equals(
                errorResponse.getDetail())) {
          throw new InvalidRequestException(errorResponse.getDetail());
        }
        if ("400 Bad Request".equals(errorResponse.getDetail())) {
          throw new InvalidRequestException(errorResponse.getDetail());
        }
      }

      throw new ConnectException(exMessage);
    }

    RecommendationResponse recommendation = response.body();

    if (InstanceCategory.SPOT.equals(serviceProvider.getInstanceCategory())) {
      recommendation.setInstanceCategory(InstanceCategory.SPOT);
      recommendation.getAccuracy().setTotalPrice(recommendation.getAccuracy().getSpotPrice());
    } else {
      recommendation.setInstanceCategory(InstanceCategory.ON_DEMAND);
      recommendation.getAccuracy().setTotalPrice(recommendation.getAccuracy().getRegularPrice());
    }

    return recommendation;
  }

  @NotNull
  private RecommendationOverviewStats getMonthlyCostAndSaving(
      @NonNull K8sServiceProvider serviceProvider, @NonNull RecommendationResponse recommendation) {
    double currentHourlyCost = serviceProvider.getCategoryAwareCost() * (double) serviceProvider.getNodeCount();
    double recommendedHourlyCost = recommendation.getAccuracy().getTotalPrice();

    final double toMonthly = 24 * 30;
    return RecommendationOverviewStats.builder()
        .totalMonthlyCost(currentHourlyCost * toMonthly)
        .totalMonthlySaving((currentHourlyCost - recommendedHourlyCost) * toMonthly)
        .build();
  }

  private void populateVMInfo(@NonNull K8sServiceProvider serviceProvider) {
    if (serviceProvider.getInstanceFamily() == null) {
      log.warn("Incomplete K8sServiceProvider {}, returning currentCost = 0", serviceProvider);
      return; // shall we use any default value?
    }

    String supportedRegion = serviceProvider.getRegion();
    if (serviceProvider.getCloudProvider() == CloudProvider.AZURE) {
      supportedRegion = VMPricingService.getSimilarRegionIfNotSupportedByBanzai(serviceProvider.getRegion());
    }

    serviceProvider.setRegion(supportedRegion);

    ProductDetails vmComputePricingInfo = vmPricingService.getComputeVMPricingInfo(
        serviceProvider.getInstanceFamily(), serviceProvider.getRegion(), serviceProvider.getCloudProvider());

    log.info("Current Pricing {}", vmComputePricingInfo);
    if (vmComputePricingInfo == null) {
      log.error("Current Pricing not available for {}", serviceProvider);
      return;
    }

    serviceProvider.setCpusPerVm(vmComputePricingInfo.getCpusPerVm());
    serviceProvider.setMemPerVm(vmComputePricingInfo.getMemPerVm());

    serviceProvider.setCostPerVmPerHr(vmComputePricingInfo.getOnDemandPrice());
    if (!isEmpty(vmComputePricingInfo.getSpotPrice())) {
      serviceProvider.setSpotCostPerVmPerHr(vmComputePricingInfo.getSpotPrice().get(0).getPrice());
    }
  }
}
