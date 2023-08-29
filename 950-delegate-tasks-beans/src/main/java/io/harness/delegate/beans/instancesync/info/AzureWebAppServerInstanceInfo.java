/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.info;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("AzureWebAppServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.beans.instancesync.info.AzureWebAppServerInstanceInfo")
public class AzureWebAppServerInstanceInfo extends ServerInstanceInfo {
  private String instanceId;
  private String instanceType;
  private String instanceName;
  private String resourceGroup;
  private String subscriptionId;
  private String appName;
  private String deploySlot;
  private String deploySlotId;
  private String appServicePlanId;
  private String hostName;
  private String instanceIp;
  private String instanceState;
}
