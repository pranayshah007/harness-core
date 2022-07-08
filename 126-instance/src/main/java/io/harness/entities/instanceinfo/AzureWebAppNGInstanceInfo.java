/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppNGInstanceInfo extends InstanceInfo {
  @NotNull private String subscriptionId;
  @NotNull private String resourceGroup;
  @NotNull private String appName;
  @NotNull private String deploySlot;
  private String instanceType;
  private String instanceId;
  private String instanceName;
  private String deploySlotId;
  private String appServicePlanId;
  private String hostName;
  private String instanceIp;
  private String instanceState;
}
