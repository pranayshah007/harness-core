/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.vmpricing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

import io.harness.batch.processing.pricing.service.support.GCPCustomInstanceDetailProvider;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.pricing.client.CloudInfoPricingClient;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.pricing.dto.cloudinfo.ProductDetailsResponse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

@Service
@Slf4j
public class VMPricingServiceImpl implements VMPricingService {
  private final CloudInfoPricingClient banzaiPricingClient;

  private static final String COMPUTE_SERVICE = "compute";

  @Autowired
  public VMPricingServiceImpl(CloudInfoPricingClient banzaiPricingClient) {
    this.banzaiPricingClient = banzaiPricingClient;
  }

  private final Cache<String, ProductDetails> vmPricingInfoCache = Caffeine.newBuilder().build();

  @Override
  public ProductDetails getComputeVMPricingInfo(String instanceType, String region, CloudProvider cloudProvider) {
    if (ImmutableSet.of(CloudProvider.ON_PREM, CloudProvider.IBM, CloudProvider.UNKNOWN).contains(cloudProvider)) {
      return null;
    }

    String supportedRegion = region;
    if (cloudProvider == CloudProvider.AZURE) {
      supportedRegion = VMPricingService.getSimilarRegionIfNotSupportedByBanzai(region);
    }

    ProductDetails vmComputePricingInfo =
        getVMPricingInfoFromCacheIfPresent(instanceType, supportedRegion, cloudProvider);

    if (vmComputePricingInfo == null
        && (ImmutableSet.of("n2-standard-16", "n2-standard-2").contains(instanceType)
            || GCPCustomInstanceDetailProvider.isCustomGCPInstance(instanceType, cloudProvider))) {
      vmComputePricingInfo = GCPCustomInstanceDetailProvider.getCustomVMPricingInfo(instanceType, supportedRegion);
    }

    if (null == vmComputePricingInfo) {
      refreshCache(supportedRegion, COMPUTE_SERVICE, cloudProvider);
      vmComputePricingInfo = getVMPricingInfoFromCacheIfPresent(instanceType, supportedRegion, cloudProvider);
    }

    return vmComputePricingInfo;
  }

  @Override
  public EcsFargatePricingInfo getFargatePricingInfo(String instanceCategory, String region) {
    if (InstanceCategory.SPOT.name().equals(instanceCategory)) {
      return EcsFargatePricingInfo.builder().region(region).cpuPrice(0.01334053).memoryPrice(0.00146489).build();
    }
    return EcsFargatePricingInfo.builder().region(region).cpuPrice(0.04656).memoryPrice(0.00511).build();
  }

  private ProductDetails getVMPricingInfoFromCacheIfPresent(
      String instanceType, String region, CloudProvider cloudProvider) {
    String vmCacheKey = getVMCacheKey(instanceType, region, cloudProvider);
    return vmPricingInfoCache.getIfPresent(vmCacheKey);
  }

  private void refreshCache(String region, String serviceName, CloudProvider cloudProvider) {
    try {
      Call<ProductDetailsResponse> pricingInfoCall =
          banzaiPricingClient.getPricingInfo(cloudProvider.getCloudProviderName(), serviceName, region);
      Response<ProductDetailsResponse> pricingInfo = pricingInfoCall.execute();
      if (null != pricingInfo.body() && null != pricingInfo.body().getProducts()) {
        List<ProductDetails> products = pricingInfo.body().getProducts();
        products.forEach(
            product -> vmPricingInfoCache.put(getVMCacheKey(product.getType(), region, cloudProvider), product));
        log.info("Cache size {}", vmPricingInfoCache.asMap().size());
        log.debug("Pricing response {} {}", pricingInfo.toString(), pricingInfo.body().getProducts());
      } else {
        log.error("Null response from cloudinfo service for params {} {} {}", region, serviceName, cloudProvider);
      }
    } catch (IOException e) {
      log.error("Exception in pricing service ", e);
    }
  }

  String getVMCacheKey(@NotNull String instanceType, @NotNull String region, @NotNull CloudProvider cloudProvider) {
    return "id_"
        + md5Hex(
            ("i_" + instanceType.toLowerCase() + "r_" + region.toLowerCase() + "c_" + cloudProvider).getBytes(UTF_8));
  }
}
