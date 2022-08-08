/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.util.InstanceSyncKey;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String subscriptionId;
  @NotNull private String resourceGroup;
  @NotNull private String appName;
  @NotNull private String deploySlot;
  private String appServicePlanId;
  private String instanceType;
  private String instanceName;
  private String deploySlotId;
  private String hostName;
  private String instanceIp;
  private String instanceState;
  private String instanceId;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder()
        .clazz(AzureWebAppInstanceInfoDTO.class)
        .part(subscriptionId)
        .part(resourceGroup)
        .part(appName)
        .part(instanceId)
        .build()
        .toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(appName).build().toString();
  }

  @Override
  public String getPodName() {
    return null;
  }

  @Override
  public String getType() {
    return "AzureWebApp";
  }
}
