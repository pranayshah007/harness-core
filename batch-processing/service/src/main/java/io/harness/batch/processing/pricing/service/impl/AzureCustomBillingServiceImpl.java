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
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
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
  private InstanceDataService instanceDataService;

  @Autowired
  public AzureCustomBillingServiceImpl(
      BigQueryHelperService bigQueryHelperService, InstanceDataService instanceDataService) {
    this.bigQueryHelperService = bigQueryHelperService;
    this.instanceDataService = instanceDataService;
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
    String resourceId = getResourceId(instanceData);
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

  String getResourceId(InstanceData instanceData) {
    String resourceId = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
        InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, instanceData.getMetaData());
    if (null == resourceId && instanceData.getInstanceType() == InstanceType.K8S_POD) {
      String parentResourceId = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
          InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, instanceData);
      InstanceData parentInstanceData = null;
      if (null != parentResourceId) {
        parentInstanceData = instanceDataService.fetchInstanceData(parentResourceId);
      } else {
        parentResourceId = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
            InstanceMetaDataConstants.PARENT_RESOURCE_ID, instanceData);
        if (null != parentResourceId) {
          parentInstanceData = instanceDataService.fetchInstanceDataWithName(
              instanceData.getAccountId(), instanceData.getClusterId(), parentResourceId, Instant.now().toEpochMilli());
        }
      }
      if (null != parentInstanceData) {
        resourceId = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
            InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, parentInstanceData.getMetaData());
      }
    }
    return resourceId;
  }
}
