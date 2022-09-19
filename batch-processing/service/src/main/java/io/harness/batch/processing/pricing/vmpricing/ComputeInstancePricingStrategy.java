/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.vmpricing;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.pricing.InstancePricingStrategy;
import io.harness.batch.processing.pricing.PricingData;
import io.harness.batch.processing.pricing.PricingSource;
import io.harness.batch.processing.pricing.fargatepricing.EcsFargateInstancePricingStrategy;
import io.harness.batch.processing.pricing.pricingprofile.PricingProfileService;
import io.harness.batch.processing.pricing.service.intfc.AwsCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
import io.harness.batch.processing.pricing.service.support.GCPCustomInstanceDetailProvider;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.ccm.cluster.entities.PricingProfile;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.pricing.dto.cloudinfo.ZonePrice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Service
public class ComputeInstancePricingStrategy implements InstancePricingStrategy {
  private final VMPricingService vmPricingService;
  private final AwsCustomBillingService awsCustomBillingService;
  private final AzureCustomBillingService azureCustomBillingService;
  private final InstanceResourceService instanceResourceService;
  private final EcsFargateInstancePricingStrategy ecsFargateInstancePricingStrategy;
  private final CustomBillingMetaDataService customBillingMetaDataService;
  private final PricingProfileService pricingProfileService;

  @Autowired
  public ComputeInstancePricingStrategy(VMPricingService vmPricingService,
      AwsCustomBillingService awsCustomBillingService, AzureCustomBillingService azureCustomBillingService,
      InstanceResourceService instanceResourceService,
      EcsFargateInstancePricingStrategy ecsFargateInstancePricingStrategy,
      CustomBillingMetaDataService customBillingMetaDataService, PricingProfileService pricingProfileService) {
    this.vmPricingService = vmPricingService;
    this.awsCustomBillingService = awsCustomBillingService;
    this.instanceResourceService = instanceResourceService;
    this.ecsFargateInstancePricingStrategy = ecsFargateInstancePricingStrategy;
    this.customBillingMetaDataService = customBillingMetaDataService;
    this.pricingProfileService = pricingProfileService;
    this.azureCustomBillingService = azureCustomBillingService;
  }

  @Override
  public PricingData getPricePerHour(InstanceData instanceData, Instant startTime, Instant endTime,
      double instanceActiveSeconds, double parentInstanceActiveSecond) {
    Map<String, String> instanceMetaData = instanceData.getMetaData();
    CloudProvider cloudProvider = CloudProvider.valueOf(instanceMetaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER));
    String zone = instanceMetaData.get(InstanceMetaDataConstants.ZONE);
    InstanceCategory instanceCategory =
        InstanceCategory.valueOf(instanceMetaData.get(InstanceMetaDataConstants.INSTANCE_CATEGORY));
    String instanceFamily = instanceMetaData.get(InstanceMetaDataConstants.INSTANCE_FAMILY);
    String computeType = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
        InstanceMetaDataConstants.COMPUTE_TYPE, instanceMetaData);
    String region = instanceMetaData.get(InstanceMetaDataConstants.REGION);
    PricingData customVMPricing = getCustomVMPricing(
        instanceData, startTime, endTime, parentInstanceActiveSecond, instanceFamily, region, cloudProvider);

    if (null == customVMPricing) {
      if (GCPCustomInstanceDetailProvider.isCustomGCPInstance(instanceFamily, cloudProvider)) {
        return GCPCustomInstanceDetailProvider.getGCPCustomInstancePricingData(instanceFamily, instanceCategory);
      } else if (ImmutableList.of(CloudProvider.ON_PREM, CloudProvider.IBM).contains(cloudProvider)) {
        return getUserCustomInstancePricingData(instanceData, instanceCategory);
      } else if (cloudProvider == CloudProvider.AWS && K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE.equals(computeType)) {
        return ecsFargateInstancePricingStrategy.getPricePerHour(
            instanceData, startTime, endTime, instanceActiveSeconds, parentInstanceActiveSecond);
      }

      ProductDetails vmComputePricingInfo =
          vmPricingService.getComputeVMPricingInfo(instanceFamily, region, cloudProvider);
      if (null == vmComputePricingInfo) {
        return getUserCustomInstancePricingData(instanceData, instanceCategory);
      }
      return PricingData.builder()
          .pricePerHour(getPricePerHour(zone, instanceCategory, vmComputePricingInfo))
          .cpuUnit(vmComputePricingInfo.getCpusPerVm() * 1024)
          .memoryMb(vmComputePricingInfo.getMemPerVm() * 1024)
          .build();
    }
    return customVMPricing;
  }

  private PricingData getUserCustomInstancePricingData(InstanceData instanceData, InstanceCategory instanceCategory) {
    PricingProfile profileData =
        pricingProfileService.fetchPricingProfile(instanceData.getAccountId(), instanceCategory);
    double cpuPricePerHr = profileData.getVCpuPricePerHr();
    double memoryPricePerHr = profileData.getMemoryGbPricePerHr();
    Double cpuUnits = instanceData.getTotalResource().getCpuUnits();
    Double memoryMb = instanceData.getTotalResource().getMemoryMb();
    if (instanceData.getInstanceType() == InstanceType.K8S_POD) {
      cpuUnits = Double.valueOf(instanceData.getMetaData().get(InstanceMetaDataConstants.PARENT_RESOURCE_CPU));
      memoryMb = Double.valueOf(instanceData.getMetaData().get(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY));
    }
    double pricePerHr = ((cpuPricePerHr * cpuUnits) / 1024) + ((memoryPricePerHr * memoryMb) / 1024);
    return PricingData.builder()
        .pricePerHour(pricePerHr)
        .cpuUnit(cpuUnits)
        .memoryMb(memoryMb)
        .pricingSource(PricingSource.HARDCODED)
        .build();
  }

  private double getPricePerHour(String zone, InstanceCategory instanceCategory, ProductDetails vmComputePricingInfo) {
    double pricePerHour = vmComputePricingInfo.getOnDemandPrice();
    if (instanceCategory == InstanceCategory.SPOT) {
      return vmComputePricingInfo.getSpotPrice()
          .stream()
          .filter(zonePrice -> zonePrice.getZone().equals(zone))
          .findFirst()
          .map(ZonePrice::getPrice)
          .orElse(pricePerHour);
    }
    return pricePerHour;
  }

  @VisibleForTesting
  PricingData getCustomVMPricing(InstanceData instanceData, Instant startTime, Instant endTime,
      double parentInstanceActiveSecond, String instanceFamily, String region, CloudProvider cloudProvider) {
    PricingData pricingData = null;
    VMInstanceBillingData vmInstanceBillingData = null;
    if (instanceFamily == null || region == null || cloudProvider == null) {
      return pricingData;
    }
    double cpuUnit = instanceData.getTotalResource().getCpuUnits();
    double memoryMb = instanceData.getTotalResource().getMemoryMb();
    Resource computeVMResource = instanceResourceService.getComputeVMResource(instanceFamily, region, cloudProvider);
    if (null != computeVMResource) {
      cpuUnit = computeVMResource.getCpuUnits();
      memoryMb = computeVMResource.getMemoryMb();
    }

    String awsDataSetId = customBillingMetaDataService.getAwsDataSetId(instanceData.getAccountId());
    String azureDataSetId = customBillingMetaDataService.getAzureDataSetId(instanceData.getAccountId());
    if (cloudProvider == CloudProvider.AWS && null != awsDataSetId) {
      vmInstanceBillingData = awsCustomBillingService.getComputeVMPricingInfo(instanceData, startTime, endTime);
    } else if (cloudProvider == CloudProvider.AZURE && null != azureDataSetId) {
      vmInstanceBillingData = azureCustomBillingService.getComputeVMPricingInfo(instanceData, startTime, endTime);
    }
    if (null != vmInstanceBillingData && !Double.isNaN(vmInstanceBillingData.getComputeCost())) {
      double pricePerHr = (vmInstanceBillingData.getComputeCost() * 3600) / parentInstanceActiveSecond;
      if (!Double.isNaN(vmInstanceBillingData.getRate()) && vmInstanceBillingData.getRate() > 0.0) {
        pricePerHr = vmInstanceBillingData.getRate();
      }
      pricingData = PricingData.builder()
                        .pricePerHour(pricePerHr)
                        .networkCost(vmInstanceBillingData.getNetworkCost())
                        .pricingSource(PricingSource.CUR_REPORT)
                        .cpuUnit(cpuUnit)
                        .memoryMb(memoryMb)
                        .build();
    }
    return pricingData;
  }
}
