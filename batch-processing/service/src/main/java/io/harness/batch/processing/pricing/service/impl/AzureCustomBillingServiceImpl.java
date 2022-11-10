/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.pricing.service.impl.util.CloudResourceIdHelper;
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
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

@OwnedBy(HarnessTeam.CE)
@Service
@Slf4j
public class AzureCustomBillingServiceImpl implements AzureCustomBillingService {
  private BigQueryHelperService bigQueryHelperService;
  private CloudResourceIdHelper cloudResourceIdHelper;

  @Autowired
  public AzureCustomBillingServiceImpl(
      BigQueryHelperService bigQueryHelperService, CloudResourceIdHelper cloudResourceIdHelper) {
    this.bigQueryHelperService = bigQueryHelperService;
    this.cloudResourceIdHelper = cloudResourceIdHelper;
  }

  private Cache<AzureCustomBillingServiceImpl.CacheKey, VMInstanceBillingData> azureResourceBillingCache =
      Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

  @Value
  private static class CacheKey {
    private String resourceId;
    private Instant startTime;
    private Instant endTime;
  }

  @Override
  public VMInstanceBillingData getComputeVMPricingInfo(InstanceData instanceData, Instant startTime, Instant endTime) {
    String resourceId = cloudResourceIdHelper.getResourceId(instanceData);
    if (null != resourceId) {
      return azureResourceBillingCache.getIfPresent(
          new AzureCustomBillingServiceImpl.CacheKey(resourceId, startTime, endTime));
    }
    return null;
  }

  @Override
  public void updateAzureVMBillingDataCache(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId) {
    Map<String, VMInstanceBillingData> azureVMBillingData =
        bigQueryHelperService.getAzureVMBillingData(resourceIds, startTime, endTime, dataSetId);
    azureVMBillingData.forEach(
        (resourceId, vmInstanceBillingData)
            -> azureResourceBillingCache.put(
                new AzureCustomBillingServiceImpl.CacheKey(resourceId, startTime, endTime), vmInstanceBillingData));
  }
}
