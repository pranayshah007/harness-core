/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.pricing.InstancePricingStrategy;
import io.harness.batch.processing.pricing.InstancePricingStrategyFactory;
import io.harness.batch.processing.pricing.PricingData;
import io.harness.batch.processing.pricing.PricingSource;
import io.harness.ccm.commons.beans.CostAttribution;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@OwnedBy(HarnessTeam.CE)
@Service
@Slf4j
public class BillingCalculationService {
  private final InstancePricingStrategyFactory instancePricingStrategyFactory;

  private final AtomicInteger atomicTripper = new AtomicInteger(0);

  @Autowired
  public BillingCalculationService(InstancePricingStrategyFactory instancePricingStrategyFactory) {
    this.instancePricingStrategyFactory = instancePricingStrategyFactory;
  }

  public String getInstanceClusterIdKey(String instanceId, String clusterId) {
    return String.format("%s:%s", instanceId, clusterId);
  }

  public Map<String, Double> getInstanceActiveSeconds(
      List<InstanceData> instanceDataList, Instant startTime, Instant endTime) {
    return instanceDataList.stream().collect(Collectors.toMap(instanceData
        -> getInstanceClusterIdKey(instanceData.getInstanceId(), instanceData.getClusterId()),
        instanceData
        -> getInstanceActiveSeconds(instanceData, startTime, endTime),
        (existing, replacement) -> existing));
  }

  public BillingData getInstanceBillingAmount(InstanceData instanceData, UtilizationData utilizationData,
      Double parentInstanceActiveSecond, Instant startTime, Instant endTime) {
    double instanceActiveSeconds = getInstanceActiveSeconds(instanceData, startTime, endTime);
    if (instanceActiveSeconds == 0) {
      return new BillingData(BillingAmountBreakup.builder()
                                 .billingAmount(BigDecimal.ZERO)
                                 .memoryBillingAmount(BigDecimal.ZERO)
                                 .cpuBillingAmount(BigDecimal.ZERO)
                                 .storageBillingAmount(BigDecimal.ZERO)
                                 .build(),
          new IdleCostData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
          new SystemCostData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), 0, 0, 0, 0, 0,
          PricingSource.PUBLIC_API);
    }
    if (null == parentInstanceActiveSecond || parentInstanceActiveSecond == 0) {
      parentInstanceActiveSecond = instanceActiveSeconds;
      if (instanceData.getInstanceType() == InstanceType.K8S_POD) {
        log.debug("Instance parent active time is 0 {} {}", instanceData.getInstanceId(), startTime);
        parentInstanceActiveSecond = 24 * 3600D;
      }
    }

    io.harness.batch.processing.pricing.PricingData pricingData =
        getPricingData(instanceData, startTime, endTime, instanceActiveSeconds, parentInstanceActiveSecond);

    return getBillingAmount(instanceData, utilizationData, pricingData, instanceActiveSeconds);
  }

  private io.harness.batch.processing.pricing.PricingData getPricingData(InstanceData instanceData, Instant startTime,
      Instant endTime, double instanceActiveSeconds, double parentInstanceActiveSecond) {
    InstancePricingStrategy instancePricingStrategy =
        instancePricingStrategyFactory.getInstancePricingStrategy(instanceData.getInstanceType());

    return instancePricingStrategy.getPricePerHour(
        instanceData, startTime, endTime, instanceActiveSeconds, parentInstanceActiveSecond);
  }

  BillingData getBillingAmount(InstanceData instanceData, UtilizationData utilizationData,
      io.harness.batch.processing.pricing.PricingData pricingData, double instanceActiveSeconds) {
    Double cpuUnit = 0D;
    Double memoryMb = 0D;
    Double storageMb = 0D;

    if (InstanceType.K8S_PV.equals(instanceData.getInstanceType())) {
      storageMb = instanceData.getStorageResource().getCapacity();
    } else if (null != instanceData.getTotalResource()) {
      cpuUnit = instanceData.getTotalResource().getCpuUnits();
      memoryMb = instanceData.getTotalResource().getMemoryMb();
      if (null != instanceData.getMetaData().get(InstanceMetaDataConstants.CLUSTER_TYPE)
          && instanceData.getMetaData().get(InstanceMetaDataConstants.CLUSTER_TYPE).equals(ClusterType.K8S.name())) {
        if (utilizationData.getAvgCpuUtilizationValue() > cpuUnit) {
          cpuUnit = utilizationData.getAvgCpuUtilizationValue();
        }
        if (utilizationData.getAvgMemoryUtilizationValue() > memoryMb) {
          memoryMb = utilizationData.getAvgMemoryUtilizationValue();
        }
      }
    } else {
      cpuUnit = pricingData.getCpuUnit();
      memoryMb = pricingData.getMemoryMb();
    }

    double pricePerHour = pricingData.getPricePerHour();
    BigDecimal billingAmount = BigDecimal.valueOf((pricePerHour * instanceActiveSeconds) / 3600);
    log.debug("Billing amount {} {} {}", billingAmount, pricePerHour, instanceActiveSeconds);

    PricingSource pricingSource =
        null != pricingData.getPricingSource() ? pricingData.getPricingSource() : PricingSource.PUBLIC_API;
    double networkCost = 0;
    if (ImmutableList.of(InstanceType.K8S_NODE, InstanceType.K8S_PV, InstanceType.K8S_PVC)
            .contains(instanceData.getInstanceType())) {
      networkCost = pricingData.getNetworkCost();
    }

    BillingAmountBreakup billingAmountForResource = getBillingAmountBreakupForResource(
        instanceData, billingAmount, cpuUnit, memoryMb, storageMb, instanceActiveSeconds, pricingData);
    IdleCostData idleCostData = getIdleCostForResource(billingAmountForResource, utilizationData, instanceData);
    SystemCostData systemCostData = getSystemCostForResource(billingAmountForResource, instanceData);

    return new BillingData(billingAmountForResource, idleCostData, systemCostData, instanceActiveSeconds,
        cpuUnit * instanceActiveSeconds, memoryMb * instanceActiveSeconds, storageMb * instanceActiveSeconds,
        networkCost, pricingSource);
  }

  BillingAmountBreakup getBillingAmountBreakupForResource(InstanceData instanceData, BigDecimal billingAmount,
      double instanceCpu, double instanceMemory, double instanceStorage, double instanceActiveSeconds,
      PricingData pricingData) {
    if (InstanceType.K8S_PV.equals(instanceData.getInstanceType())) {
      return BillingAmountBreakup.builder()
          .billingAmount(billingAmount)
          .cpuBillingAmount(BigDecimal.ZERO)
          .memoryBillingAmount(BigDecimal.ZERO)
          .storageBillingAmount(billingAmount)
          .build();
    } else if (instanceData.getInstanceType().getCostAttribution() == CostAttribution.PARTIAL) {
      Map<String, String> instanceMetaData = instanceData.getMetaData();
      Double parentInstanceCpu = Double.valueOf(instanceMetaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_CPU));
      Double parentInstanceMemory =
          Double.valueOf(instanceMetaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY));

      double cpuFraction = parentInstanceCpu == 0 ? 0 : (instanceCpu / parentInstanceCpu);
      double memoryFraction = parentInstanceMemory == 0 ? 0 : (instanceMemory / parentInstanceMemory);

      BigDecimal instanceUsage = BigDecimal.valueOf((cpuFraction + memoryFraction) * 0.5);
      return BillingAmountBreakup.builder()
          .billingAmount(instanceUsage.multiply(billingAmount))
          .cpuBillingAmount(billingAmount.multiply(BigDecimal.valueOf(cpuFraction * 0.5)))
          .memoryBillingAmount(billingAmount.multiply(BigDecimal.valueOf(memoryFraction * 0.5)))
          .storageBillingAmount(BigDecimal.ZERO)
          .build();
    }

    BigDecimal cpuBillingAmount = billingAmount.multiply(BigDecimal.valueOf(0.5));
    BigDecimal memoryBillingAmount = billingAmount.multiply(BigDecimal.valueOf(0.5));
    if (pricingData.getCpuPricePerHour() > 0.0 && pricingData.getMemoryPricePerHour() > 0.0) {
      cpuBillingAmount = BigDecimal.valueOf((pricingData.getCpuPricePerHour() * instanceActiveSeconds) / 3600);
      memoryBillingAmount = BigDecimal.valueOf((pricingData.getMemoryPricePerHour() * instanceActiveSeconds) / 3600);
    }

    return BillingAmountBreakup.builder()
        .billingAmount(billingAmount)
        .cpuBillingAmount(cpuBillingAmount)
        .memoryBillingAmount(memoryBillingAmount)
        .storageBillingAmount(BigDecimal.ZERO)
        .build();
  }

  SystemCostData getSystemCostForResource(BillingAmountBreakup billingDataForResource, InstanceData instanceData) {
    BigDecimal cpuSystemCost = BigDecimal.ZERO;
    BigDecimal memorySystemCost = BigDecimal.ZERO;
    BigDecimal systemCost = BigDecimal.ZERO;
    if (instanceData.getAllocatableResource() != null && instanceData.getTotalResource() != null) {
      BigDecimal cpuBillingAmount = billingDataForResource.getCpuBillingAmount();
      BigDecimal memoryBillingAmount = billingDataForResource.getMemoryBillingAmount();
      if (instanceData.getTotalResource().getCpuUnits() > 0) {
        cpuSystemCost = BigDecimal.valueOf(cpuBillingAmount.doubleValue()
            * (1
                - (instanceData.getAllocatableResource().getCpuUnits()
                    / instanceData.getTotalResource().getCpuUnits())));
      }
      if (instanceData.getTotalResource().getMemoryMb() > 0) {
        memorySystemCost = BigDecimal.valueOf(memoryBillingAmount.doubleValue()
            * (1
                - (instanceData.getAllocatableResource().getMemoryMb()
                    / instanceData.getTotalResource().getMemoryMb())));
      }
      systemCost = cpuSystemCost.add(memorySystemCost);
    }
    return new SystemCostData(systemCost, cpuSystemCost, memorySystemCost);
  }

  @VisibleForTesting
  public IdleCostData getIdleCostForResource(
      BillingAmountBreakup billingDataForResource, UtilizationData utilizationData, InstanceData instanceData) {
    if (utilizationData == null) {
      return new IdleCostData(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    BigDecimal cpuIdleCost = BigDecimal.ZERO;
    BigDecimal memoryIdleCost = BigDecimal.ZERO;
    BigDecimal storageIdleCost = BigDecimal.ZERO;

    if (utilizationData.getAvgCpuUtilization() < 1) {
      cpuIdleCost = BigDecimal.valueOf(
          billingDataForResource.getCpuBillingAmount().doubleValue() * (1 - utilizationData.getAvgCpuUtilization()));
    }
    if (utilizationData.getAvgMemoryUtilization() < 1) {
      memoryIdleCost = BigDecimal.valueOf(billingDataForResource.getMemoryBillingAmount().doubleValue()
          * (1 - utilizationData.getAvgMemoryUtilization()));
    }

    double storageRequest = utilizationData.getMaxStorageRequestValue();
    double storageUsage = utilizationData.getMaxStorageUsageValue();
    StorageResource storageResource = instanceData.getStorageResource();
    if (instanceData.getInstanceType() == InstanceType.K8S_PV) {
      // in one cases (with NFS PV), the PV.Capacity is much less than claimed PVC.Request
      if (storageUsage <= storageRequest && storageRequest <= storageResource.getCapacity()
          && storageResource.getCapacity() > 0) {
        storageIdleCost = BigDecimal.valueOf(billingDataForResource.getStorageBillingAmount().doubleValue()
            * (storageRequest - storageUsage) / storageResource.getCapacity());
      } else if (storageResource.getCapacity() == 0) {
        // this is an edge case will rarely happen,
        // 0 occurence between 25th March'21 to 1st April'21 (7 days) across all accounts.
        log.warn("storageResource.getCapacity() == 0 for InstanceData; AccountId: {}, InstanceId:{}",
            instanceData.getAccountId(), instanceData.getInstanceId());
      } else {
        // using atomicTripper to reduce the verbosity
        if (atomicTripper.getAndIncrement() % 1000 == 0) {
          // Usage:45053.375, Request:256000.0, storageResource=StorageResource(capacity=65536.0),
          // instanceId=7b89e3c2-e1bb-4ed8-8bf1-4461977eb239, The PV configuration applied says 64GB of Capacity
          // But from Pod stats client we see that the request is 250 GB ( = 256000.0 / 1024.0) This
          // inconsistency is unknown. "But is very frequent" in NFS and some non-GCP based PV's
          log.warn("THIS IS HARMLESS, Inconsistent PV storage value data; Usage:{}, Request:{}, InstanceData:{}",
              utilizationData.getMaxStorageUsageValue(), utilizationData.getMaxStorageRequestValue(), instanceData);
        }
      }
    }

    BigDecimal idleCost = BigDecimal.ZERO.add(cpuIdleCost).add(memoryIdleCost).add(storageIdleCost);
    return new IdleCostData(idleCost, cpuIdleCost, memoryIdleCost, storageIdleCost);
  }

  double getInstanceActiveSeconds(InstanceData instanceData, Instant startTime, Instant endTime) {
    if ((null != instanceData.getUsageStartTime() && instanceData.getUsageStartTime().isAfter(endTime))
        || (null != instanceData.getUsageStopTime() && instanceData.getUsageStopTime().isBefore(startTime))) {
      return 0;
    }
    Long instanceActiveSeconds = getActiveInstanceTimeInInterval(instanceData, startTime, endTime);
    double minChargeableSeconds = instanceData.getInstanceType().getMinChargeableSeconds();
    if (null == instanceData.getUsageStopTime() || instanceActiveSeconds > minChargeableSeconds) {
      return instanceActiveSeconds;
    } else {
      double totalInstanceActiveSeconds = getTotalActiveInstanceTimeInterval(instanceData);
      if (totalInstanceActiveSeconds > minChargeableSeconds) {
        return instanceActiveSeconds;
      } else {
        return minChargeableSeconds - totalInstanceActiveSeconds + instanceActiveSeconds;
      }
    }
  }

  private Long getTotalActiveInstanceTimeInterval(InstanceData instanceData) {
    return instanceData.getUsageStopTime().getEpochSecond() - instanceData.getUsageStartTime().getEpochSecond();
  }

  Long getActiveInstanceTimeInInterval(InstanceData instanceData, Instant startTime, Instant endTime) {
    Instant activeStartTime = startTime;
    if (instanceData.getUsageStartTime().isAfter(startTime)) {
      activeStartTime = instanceData.getUsageStartTime();
    }

    Instant activeEndTime = endTime;
    if (null != instanceData.getUsageStopTime() && instanceData.getUsageStopTime().isBefore(endTime)) {
      activeEndTime = instanceData.getUsageStopTime();
    }
    return activeEndTime.getEpochSecond() - activeStartTime.getEpochSecond();
  }
}
