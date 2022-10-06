/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instancesyncmonitoring.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.AutoMetricContext;

import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncMetricContext extends AutoMetricContext {
  public InstanceSyncMetricContext(InstanceSyncMetricDetails instanceSyncMetricDetails) {
    if (StringUtils.isNotBlank(instanceSyncMetricDetails.getAccountId())) {
      put("accountId", instanceSyncMetricDetails.getAccountId());
    }
    if (StringUtils.isNotBlank(instanceSyncMetricDetails.getAppId())) {
      put("appId", instanceSyncMetricDetails.getAppId());
    }
    if (StringUtils.isNotBlank(instanceSyncMetricDetails.getOrgId())) {
      put("orgId", instanceSyncMetricDetails.getOrgId());
    }
    if (StringUtils.isNotBlank(instanceSyncMetricDetails.getProjectId())) {
      put("projectId", instanceSyncMetricDetails.getProjectId());
    }
    put("isNg", Boolean.toString(instanceSyncMetricDetails.isNg()));
    if (StringUtils.isNotBlank(instanceSyncMetricDetails.getDeploymentType())) {
      put("deploymentType", instanceSyncMetricDetails.getDeploymentType());
    }
    if (StringUtils.isNotBlank(instanceSyncMetricDetails.getStatus())) {
      put("status", instanceSyncMetricDetails.getStatus());
    }
  }
}
