/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.service.impl.util;

import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;

import com.google.inject.Inject;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CloudResourceIdHelper {
  @Inject @Autowired private InstanceDataService instanceDataService;

  public String getResourceId(InstanceData instanceData) {
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
