/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.service.support;

import io.harness.batch.processing.pricing.PricingData;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.pricing.dto.cloudinfo.ZonePrice;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class GCPCustomInstanceDetailProvider {
  private final String GCP_CUSTOM_INSTANCE_PREFIX = "custom-";

  private final String GENERAL_PURPOSE = "General purpose";

  public boolean isCustomGCPInstance(String instanceType, CloudProvider cloudProvider) {
    return CloudProvider.GCP.equals(cloudProvider) && instanceType != null
        && instanceType.contains(GCP_CUSTOM_INSTANCE_PREFIX);
  }

  public Resource getCustomGcpInstanceResource(@NonNull String instanceType) {
    String[] split = instanceType.split("-");
    double cpu = Double.parseDouble(split[split.length - 2]);
    double memory = Double.parseDouble(split[split.length - 1]);
    return Resource.builder().cpuUnits(cpu * 1024.0).memoryMb(memory).build();
  }

  public PricingData getGCPCustomInstancePricingData(
      @NonNull String instanceFamily, InstanceCategory instanceCategory) {
    double cpuPricePerHr = 0.033174;
    double memoryPricePerHr = 0.004446;
    if (instanceCategory == InstanceCategory.SPOT) {
      cpuPricePerHr = 0.00698;
      memoryPricePerHr = 0.00094;
    }
    Resource resource = getCustomGcpInstanceResource(instanceFamily);
    double cpu = resource.getCpuUnits();
    double memory = resource.getMemoryMb();
    double pricePerHr = ((cpuPricePerHr / 1024) * cpu) + ((memoryPricePerHr / 1024) * memory);
    return PricingData.builder().pricePerHour(pricePerHr).cpuUnit(cpu).memoryMb(memory).build();
  }

  public ProductDetails getCustomVMPricingInfo(@NonNull String instanceType, String region) {
    if ("n2-standard-16".equals(instanceType)) {
      return getN2Standard16VMPricingInfo(instanceType, region);
    }
    if ("n2-standard-2".equals(instanceType)) {
      return getN2Standard2VMPricingInfo(instanceType, region);
    }

    final Resource resource = getCustomGcpInstanceResource(instanceType);
    final double cpuUnits = resource.getCpuUnits() / 1024.0; // vCPU
    final double memoryUnits = resource.getMemoryMb() / 1024.0; // GiB

    ProductDetails vmComputePricingInfo =
        ProductDetails.builder().cpusPerVm(cpuUnits).memPerVm(memoryUnits).type(instanceType).build();

    if (instanceType.startsWith("n2d-")) {
      return populateAndGetN2DCustomPricing(vmComputePricingInfo, region);
    }

    if (instanceType.startsWith("n2-")) {
      return populateAndGetN2CustomPricing(vmComputePricingInfo, region);
    }

    if (instanceType.startsWith("e2-")) {
      return populateAndGetE2CustomPricing(vmComputePricingInfo, region);
    }

    if (instanceType.startsWith("n1-") || instanceType.startsWith(GCP_CUSTOM_INSTANCE_PREFIX)) {
      return populateAndGetN1CustomPricing(vmComputePricingInfo, region);
    }

    return null;
  }

  private ProductDetails populateAndGetN2DCustomPricing(ProductDetails pricingInfo, String region) {
    CustomPricing customPricing = CustomPricing.builder()
                                      .cpuPrice(0.028877)
                                      .memoryPrice(0.003870)
                                      .spotCpuPrice(0.006980)
                                      .spotMemoryPrice(0.000940)
                                      .build();

    pricingInfo.setCategory(GENERAL_PURPOSE);
    return populateCommonFields(pricingInfo, region, customPricing);
  }

  private ProductDetails populateAndGetN2CustomPricing(ProductDetails pricingInfo, String region) {
    CustomPricing customPricing = CustomPricing.builder()
                                      .cpuPrice(0.033174)
                                      .memoryPrice(0.004446)
                                      .spotCpuPrice(0.00802)
                                      .spotMemoryPrice(0.00108)
                                      .build();

    pricingInfo.setCategory(GENERAL_PURPOSE);
    return populateCommonFields(pricingInfo, region, customPricing);
  }

  private ProductDetails populateAndGetE2CustomPricing(ProductDetails pricingInfo, String region) {
    CustomPricing customPricing = CustomPricing.builder()
                                      .cpuPrice(0.022890)
                                      .memoryPrice(0.003067)
                                      .spotCpuPrice(0.006867)
                                      .spotMemoryPrice(0.000920)
                                      .build();

    pricingInfo.setCategory(GENERAL_PURPOSE);
    return populateCommonFields(pricingInfo, region, customPricing);
  }

  private ProductDetails populateAndGetN1CustomPricing(ProductDetails pricingInfo, String region) {
    CustomPricing customPricing = CustomPricing.builder()
                                      .cpuPrice(0.033174)
                                      .memoryPrice(0.004446)
                                      .spotCpuPrice(0.00698)
                                      .spotMemoryPrice(0.00094)
                                      .build();

    pricingInfo.setCategory(GENERAL_PURPOSE);
    return populateCommonFields(pricingInfo, region, customPricing);
  }

  /**
   * resource price ('cpuPrice' per vCPU per hour, 'memoryPrice' per GiB per hour)
   */
  @Builder
  private static class CustomPricing {
    double cpuPrice;
    double memoryPrice;
    double spotCpuPrice;
    double spotMemoryPrice;
  }

  @NotNull
  private ProductDetails populateCommonFields(ProductDetails pricingInfo, String region, CustomPricing customPricing) {
    double onDemandPrice =
        customPricing.cpuPrice * pricingInfo.getCpusPerVm() + customPricing.memoryPrice * pricingInfo.getMemPerVm();
    pricingInfo.setOnDemandPrice(onDemandPrice);

    double spotPrice = customPricing.spotCpuPrice * pricingInfo.getCpusPerVm()
        + customPricing.spotMemoryPrice * pricingInfo.getMemPerVm();
    pricingInfo.setSpotPrice(getZonePriceList(spotPrice, region, ImmutableList.of("a", "b", "c")));

    pricingInfo.setNetworkPrice(0.0D);

    return pricingInfo;
  }

  private ProductDetails getN2Standard16VMPricingInfo(String instanceType, String region) {
    return ProductDetails.builder()
        .category(GENERAL_PURPOSE)
        .type(instanceType)
        .onDemandPrice(0.7769)
        .spotPrice(getZonePriceList(0.1880, region, ImmutableList.of("a", "b", "c")))
        .networkPrice(0.0)
        .cpusPerVm(16D)
        .memPerVm(64D)
        .build();
  }

  private ProductDetails getN2Standard2VMPricingInfo(String instanceType, String region) {
    return ProductDetails.builder()
        .category(GENERAL_PURPOSE)
        .type(instanceType)
        .onDemandPrice(0.097118)
        .spotPrice(getZonePriceList(0.02354, region, ImmutableList.of("a", "b", "c")))
        .networkPrice(0.0)
        .cpusPerVm(2D)
        .memPerVm(8D)
        .build();
  }

  private List<ZonePrice> getZonePriceList(double spotPrice, String region, List<String> zones) {
    return zones.stream()
        .map(zone -> ZonePrice.builder().price(spotPrice).zone(region + "-" + zone).build())
        .collect(Collectors.toList());
  }
}
