/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.service.impl;

import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.pricing.service.impl.util.CloudResourceIdHelper;
import io.harness.batch.processing.pricing.service.intfc.AwsCustomBillingService;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.ccm.commons.entities.batch.InstanceData;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AwsCustomBillingServiceImpl implements AwsCustomBillingService {
  private BigQueryHelperService bigQueryHelperService;
  private CloudResourceIdHelper cloudResourceIdHelper;

  @Autowired
  public AwsCustomBillingServiceImpl(
      BigQueryHelperService bigQueryHelperService, CloudResourceIdHelper cloudResourceIdHelper) {
    this.bigQueryHelperService = bigQueryHelperService;
    this.cloudResourceIdHelper = cloudResourceIdHelper;
  }

  private Cache<CacheKey, VMInstanceBillingData> awsResourceBillingCache =
      Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

  @Value
  private static class CacheKey {
    private String resourceId;
    private Instant startTime;
    private Instant endTime;
  }

  @Override
  public void updateAwsEC2BillingDataCache(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId) {
    Map<String, VMInstanceBillingData> awsEC2BillingData =
        bigQueryHelperService.getAwsEC2BillingData(resourceIds, startTime, endTime, dataSetId);
    awsEC2BillingData.forEach(
        (resourceId, vmInstanceBillingData)
            -> awsResourceBillingCache.put(new CacheKey(resourceId, startTime, endTime), vmInstanceBillingData));
  }

  @Override
  public void updateEksFargateDataCache(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId) {
    Map<String, VMInstanceBillingData> eksFargateBillingData =
        bigQueryHelperService.getEKSFargateBillingData(resourceIds, startTime, endTime, dataSetId);
    eksFargateBillingData.forEach(
        (resourceId, vmInstanceBillingData)
            -> awsResourceBillingCache.put(new CacheKey(resourceId, startTime, endTime), vmInstanceBillingData));
  }

  @Override
  public VMInstanceBillingData getComputeVMPricingInfo(InstanceData instanceData, Instant startTime, Instant endTime) {
    String resourceId = cloudResourceIdHelper.getResourceId(instanceData);
    if (null != resourceId) {
      return awsResourceBillingCache.getIfPresent(new CacheKey(resourceId, startTime, endTime));
    }
    return null;
  }

  @Override
  public VMInstanceBillingData getFargateVMPricingInfo(String resourceId, Instant startTime, Instant endTime) {
    return awsResourceBillingCache.getIfPresent(new CacheKey(resourceId, startTime, endTime));
  }
}
