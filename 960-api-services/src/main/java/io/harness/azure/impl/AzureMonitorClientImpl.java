/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_ID_NAME_NULL_VALIDATION_MSG;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.model.AzureConfig;
import io.harness.utils.DateTimeUtils;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.monitor.models.EventData;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

@Singleton
@Slf4j
public class AzureMonitorClientImpl extends AzureClient implements AzureMonitorClient {
  @Override
  public List<EventData> listEventDataWithAllPropertiesByResourceId(
      AzureConfig azureConfig, String subscriptionId, DateTime startTime, DateTime endTime, final String resourceId) {
    if (isBlank(resourceId)) {
      throw new IllegalArgumentException(RESOURCE_ID_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing activity log event data with all properties for resourceId {}, startTime {}, endTime: {}",
        resourceId, startTime.toDateTime(), endTime.toDateTime());
    return azure.activityLogs()
        .defineQuery()
        .startingFrom(DateTimeUtils.fromJodaDateTime2OffestDateTime(startTime))
        .endsBefore(DateTimeUtils.fromJodaDateTime2OffestDateTime(endTime))
        .withAllPropertiesInResponse()
        .filterByResource(resourceId)
        .execute()
        .stream()
        .collect(Collectors.toList());
  }

  public List<EventData> listEventDataWithAllPropertiesByResourceGroupName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, DateTime startTime, DateTime endTime) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug(
        "Start listing activity log event data with all properties by resourceGroupName {}, startTime {}, endTime: {}",
        resourceGroupName, startTime.toDateTime(), endTime.toDateTime());
    return azure.activityLogs()
        .defineQuery()
        .startingFrom(DateTimeUtils.fromJodaDateTime2OffestDateTime(startTime))
        .endsBefore(DateTimeUtils.fromJodaDateTime2OffestDateTime(endTime))
        .withAllPropertiesInResponse()
        .filterByResourceGroup(resourceGroupName)
        .execute()
        .stream()
        .collect(Collectors.toList());
  }
}
