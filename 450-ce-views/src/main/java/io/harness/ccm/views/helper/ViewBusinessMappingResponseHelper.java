/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.businessmapping.entities.SharedCost;
import io.harness.ccm.views.businessmapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessmapping.entities.SharingStrategy;
import io.harness.ccm.views.businessmapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.EntitySharedCostDetails;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint.QLCEViewEntityStatsDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;

import com.google.cloud.Timestamp;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ViewBusinessMappingResponseHelper {
  @Inject private BusinessMappingService businessMappingService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private ViewsQueryBuilder viewsQueryBuilder;

  public QLCEViewGridData costCategoriesPostFetchResponseUpdate(QLCEViewGridData response, String businessMappingId,
      List<BusinessMapping> sharedCostBusinessMappings, Map<String, Double> sharedCosts) {
    List<QLCEViewEntityStatsDataPoint> updatedDataPoints = new ArrayList<>();
    if (businessMappingId != null) {
      BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      if (businessMapping.getUnallocatedCost() != null) {
        UnallocatedCostStrategy strategy = businessMapping.getUnallocatedCost().getStrategy();
        switch (strategy) {
          case DISPLAY_NAME:
            for (QLCEViewEntityStatsDataPoint dataPoint : response.getData()) {
              if (dataPoint.getName().equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedDataPoints.add(QLCEViewEntityStatsDataPoint.builder()
                                          .name(businessMapping.getUnallocatedCost().getLabel())
                                          .id(businessMapping.getUnallocatedCost().getLabel())
                                          .pricingSource(dataPoint.getPricingSource())
                                          .cost(dataPoint.getCost())
                                          .costTrend(dataPoint.getCostTrend())
                                          .isClusterPerspective(dataPoint.isClusterPerspective())
                                          .clusterData(dataPoint.getClusterData())
                                          .instanceDetails(dataPoint.getInstanceDetails())
                                          .storageDetails(dataPoint.getStorageDetails())
                                          .build());
              } else {
                updatedDataPoints.add(dataPoint);
              }
            }
            break;
          case HIDE:
            for (QLCEViewEntityStatsDataPoint dataPoint : response.getData()) {
              if (!dataPoint.getName().equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())
                  && !dataPoint.getName().equals(businessMapping.getUnallocatedCost().getLabel())) {
                updatedDataPoints.add(dataPoint);
              }
            }
            break;
          case SHARE:
          default:
            throw new InvalidRequestException(
                "Invalid Unallocated Cost Strategy / Unallocated Cost Strategy not supported");
        }
      }
    } else {
      updatedDataPoints = response.getData();
    }

    if (!sharedCostBusinessMappings.isEmpty()) {
      updatedDataPoints = addSharedCostsFromFilters(updatedDataPoints, sharedCosts);
    }

    updatedDataPoints =
        updatedDataPoints.stream()
            .filter(dataPoint
                -> Objects.nonNull(dataPoint.getCost()) && Double.compare(dataPoint.getCost().doubleValue(), 0.0D) > 0)
            .collect(Collectors.toList());
    updatedDataPoints.sort(
        (dataPoints1,
            dataPoints2) -> Double.compare(dataPoints2.getCost().doubleValue(), dataPoints1.getCost().doubleValue()));

    return QLCEViewGridData.builder().data(updatedDataPoints).fields(response.getFields()).build();
  }

  public List<QLCEViewEntityStatsDataPoint> addSharedCosts(List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints,
      Map<String, Double> sharedCosts, BusinessMapping businessMapping) {
    double totalCost = 0.0;
    double numberOfEntities = 0.0;
    List<String> costTargetNames = businessMapping.getCostTargets() != null
        ? businessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList())
        : Collections.emptyList();

    for (QLCEViewEntityStatsDataPoint dataPoint : entityStatsDataPoints) {
      if (costTargetNames.contains(dataPoint.getName())) {
        totalCost += dataPoint.getCost().doubleValue();
        numberOfEntities += 1;
      }
    }

    List<QLCEViewEntityStatsDataPoint> updatedDataPoints = new ArrayList<>();
    for (QLCEViewEntityStatsDataPoint dataPoint : entityStatsDataPoints) {
      double finalCost = !costTargetNames.contains(dataPoint.getName()) ? dataPoint.getCost().doubleValue()
                                                                        : dataPoint.getCost().doubleValue()
              + calculateSharedCost(businessMapping.getSharedCosts(), sharedCosts, dataPoint.getName(),
                  dataPoint.getCost().doubleValue(), totalCost, numberOfEntities);
      final QLCEViewEntityStatsDataPointBuilder qlceViewEntityStatsDataPointBuilder =
          QLCEViewEntityStatsDataPoint.builder();
      // Setting cost trend 0 because shared cost trend is not computed
      qlceViewEntityStatsDataPointBuilder.id(dataPoint.getId()).name(dataPoint.getName()).cost(finalCost).costTrend(0);
      updatedDataPoints.add(qlceViewEntityStatsDataPointBuilder.build());
    }
    return updatedDataPoints;
  }

  public List<QLCEViewEntityStatsDataPoint> addSharedCostsFromFilters(
      List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints, Map<String, Double> sharedCosts) {
    Set<String> entitiesToUpdate = sharedCosts.keySet();
    List<QLCEViewEntityStatsDataPoint> updatedDataPoints = new ArrayList<>();
    Map<String, Boolean> sharedCostAdded = new HashMap<>();
    for (QLCEViewEntityStatsDataPoint dataPoint : entityStatsDataPoints) {
      double finalCost = dataPoint.getCost().doubleValue();
      Number finalCostTrend = dataPoint.getCostTrend();
      if (entitiesToUpdate.contains(dataPoint.getName())) {
        finalCost += sharedCosts.get(dataPoint.getName());
        finalCostTrend = 0;
        sharedCostAdded.put(dataPoint.getName(), true);
      }
      updatedDataPoints.add(QLCEViewEntityStatsDataPoint.builder()
                                .id(dataPoint.getId())
                                .name(dataPoint.getName())
                                .cost(viewsQueryHelper.getRoundedDoubleValue(finalCost))
                                .costTrend(finalCostTrend)
                                .build());
    }

    for (String entity : entitiesToUpdate) {
      if (!sharedCostAdded.containsKey(entity)) {
        sharedCostAdded.put(entity, true);
        updatedDataPoints.add(QLCEViewEntityStatsDataPoint.builder()
                                  .id(entity)
                                  .name(entity)
                                  .cost(viewsQueryHelper.getRoundedDoubleValue(sharedCosts.get(entity)))
                                  .costTrend(0)
                                  .build());
      }
    }

    return updatedDataPoints;
  }

  public double calculateSharedCost(List<SharedCost> sharedCostBuckets, Map<String, Double> sharedCosts, String entity,
      double entityCost, double totalCost, double totalEntities) {
    double totalSharedCost = 0.0;
    for (SharedCost sharedCostBucket : sharedCostBuckets) {
      SharingStrategy sharingStrategy = totalCost != 0 ? sharedCostBucket.getStrategy() : SharingStrategy.EQUAL;
      double sharedCost =
          sharedCosts.getOrDefault(viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()), 0.0D);
      switch (sharingStrategy) {
        case PROPORTIONAL:
          totalSharedCost += sharedCost * (entityCost / totalCost);
          break;
        case EQUAL:
          totalSharedCost += sharedCost * (1.0 / totalEntities);
          break;
        case FIXED:
          for (final SharedCostSplit sharedCostSplit : sharedCostBucket.getSplits()) {
            if (entity.equals(sharedCostSplit.getCostTargetName())) {
              totalSharedCost += sharedCost * (sharedCostSplit.getPercentageContribution() / 100.0D);
              break;
            }
          }
          break;
        default:
          log.error("Invalid shared cost strategy for shared cost bucket: {}", sharedCostBucket);
          break;
      }
    }
    return totalSharedCost;
  }

  public Map<String, List<EntitySharedCostDetails>> calculateSharedCostPerEntity(
      BusinessMapping sharedCostBusinessMapping, Map<String, Double> sharedCosts, Map<String, Double> entityCosts,
      double totalCost) {
    List<SharedCost> sharedCostBuckets = sharedCostBusinessMapping.getSharedCosts();
    double totalEntities = sharedCostBusinessMapping.getCostTargets().size();
    List<String> costTargets =
        sharedCostBusinessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());
    costTargets.forEach(costTarget -> {
      if (!entityCosts.containsKey(costTarget)) {
        entityCosts.put(costTarget, 0.0);
      }
    });
    Map<String, List<EntitySharedCostDetails>> sharedCostDetailsPerEntity = new HashMap<>();
    entityCosts.keySet().forEach(entity -> {
      List<EntitySharedCostDetails> entitySharedCostDetails = new ArrayList<>();
      sharedCostBuckets.forEach(sharedCostBucket
          -> entitySharedCostDetails.add(
              EntitySharedCostDetails.builder()
                  .sharedCostBucketName(sharedCostBucket.getName())
                  .cost(calculateSharedCost(Collections.singletonList(sharedCostBucket), sharedCosts, entity,
                      entityCosts.get(entity), totalCost, totalEntities))
                  .build()));
      sharedCostDetailsPerEntity.put(entity, entitySharedCostDetails);
    });
    return sharedCostDetailsPerEntity;
  }

  public List<String> costCategoriesPostFetchResponseUpdate(List<String> response, String businessMappingId) {
    if (businessMappingId != null) {
      BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      if (businessMapping.getUnallocatedCost() != null) {
        List<String> updatedResponse = new ArrayList<>();
        UnallocatedCostStrategy strategy = businessMapping.getUnallocatedCost().getStrategy();
        switch (strategy) {
          case DISPLAY_NAME:
            response.forEach(value -> {
              if (value.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedResponse.add(businessMapping.getUnallocatedCost().getLabel());
              } else {
                updatedResponse.add(value);
              }
            });
            break;
          case HIDE:
            response.forEach(value -> {
              if (!value.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedResponse.add(value);
              }
            });
            break;
          case SHARE:
          default:
            throw new InvalidRequestException(
                "Invalid Unallocated Cost Strategy / Unallocated Cost Strategy not supported");
        }
        return updatedResponse;
      }
    }
    return response;
  }

  public double calculateSharedCostForTimestamp(Map<String, Map<Timestamp, Double>> sharedCosts, Timestamp timestamp,
      BusinessMapping sharedCostBusinessMapping, String entity, Double entityCost, Double numberOfEntities,
      Double totalCost) {
    double sharedCost = 0.0;
    for (SharedCost sharedCostBucket : sharedCostBusinessMapping.getSharedCosts()) {
      Map<Timestamp, Double> sharedCostsPerTimestamp =
          sharedCosts.get(viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()));
      if (Objects.nonNull(sharedCostsPerTimestamp)) {
        double sharedCostForGivenTimestamp = sharedCostsPerTimestamp.getOrDefault(timestamp, 0.0D);
        SharingStrategy sharingStrategy = totalCost != 0 ? sharedCostBucket.getStrategy() : SharingStrategy.EQUAL;
        switch (sharingStrategy) {
          case PROPORTIONAL:
            sharedCost += sharedCostForGivenTimestamp * (entityCost / totalCost);
            break;
          case EQUAL:
            sharedCost += sharedCostForGivenTimestamp * (1.0 / numberOfEntities);
            break;
          case FIXED:
            for (final SharedCostSplit sharedCostSplit : sharedCostBucket.getSplits()) {
              if (entity.equals(sharedCostSplit.getCostTargetName())) {
                sharedCost += sharedCostForGivenTimestamp * (sharedCostSplit.getPercentageContribution() / 100.0D);
                break;
              }
            }
            break;
          default:
            log.error("Invalid shared cost strategy for shared cost bucket: {}", sharedCostBucket);
            break;
        }
      }
    }
    return sharedCost;
  }

  public void updateSharedCostMap(Map<String, Map<Timestamp, Double>> sharedCostFromGroupBy, Double sharedCostValue,
      String sharedCostName, Timestamp timeStamp) {
    if (!sharedCostFromGroupBy.containsKey(sharedCostName)) {
      sharedCostFromGroupBy.put(sharedCostName, new HashMap<>());
    }
    if (!sharedCostFromGroupBy.get(sharedCostName).containsKey(timeStamp)) {
      sharedCostFromGroupBy.get(sharedCostName).put(timeStamp, 0.0);
    }
    Double currentValue = sharedCostFromGroupBy.get(sharedCostName).get(timeStamp);
    sharedCostFromGroupBy.get(sharedCostName).put(timeStamp, currentValue + sharedCostValue);
  }

  public List<QLCEViewEntityStatsDataPoint> subtractDuplicateSharedCostFromUnattributed(
      final List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints, final double totalSharedCostsInUnattributed,
      final BusinessMapping businessMapping) {
    final List<QLCEViewEntityStatsDataPoint> modifiedEntityStatsDataPoints = new ArrayList<>();
    for (final QLCEViewEntityStatsDataPoint entityStatsDataPoint : entityStatsDataPoints) {
      if (Objects.nonNull(businessMapping.getUnallocatedCost())
          && entityStatsDataPoint.getName().equals(businessMapping.getUnallocatedCost().getLabel())) {
        final Number finalCost = entityStatsDataPoint.getCost().doubleValue() - totalSharedCostsInUnattributed;
        modifiedEntityStatsDataPoints.add(QLCEViewEntityStatsDataPoint.builder()
                                              .name(entityStatsDataPoint.getName())
                                              .id(entityStatsDataPoint.getId())
                                              .pricingSource(entityStatsDataPoint.getPricingSource())
                                              .cost(Math.max(finalCost.doubleValue(), 0.0D))
                                              .costTrend(entityStatsDataPoint.getCostTrend())
                                              .isClusterPerspective(entityStatsDataPoint.isClusterPerspective())
                                              .clusterData(entityStatsDataPoint.getClusterData())
                                              .instanceDetails(entityStatsDataPoint.getInstanceDetails())
                                              .storageDetails(entityStatsDataPoint.getStorageDetails())
                                              .build());
      } else {
        modifiedEntityStatsDataPoints.add(entityStatsDataPoint);
      }
    }
    return modifiedEntityStatsDataPoints;
  }
}
