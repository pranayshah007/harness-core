/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConfig;

import com.azure.resourcemanager.monitor.models.EventData;
import java.util.List;
import org.joda.time.DateTime;

public interface AzureMonitorClient {
  /**
   * List event data with all properties by resource id.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param startTime
   * @param endTime
   * @param resourceId
   * @return
   */
  List<EventData> listEventDataWithAllPropertiesByResourceId(
      AzureConfig azureConfig, String subscriptionId, DateTime startTime, DateTime endTime, String resourceId);

  /**
   * List event data with all properties by resource group name.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param startTime
   * @param endTime
   * @return
   */
  List<EventData> listEventDataWithAllPropertiesByResourceGroupName(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, DateTime startTime, DateTime endTime);
}
