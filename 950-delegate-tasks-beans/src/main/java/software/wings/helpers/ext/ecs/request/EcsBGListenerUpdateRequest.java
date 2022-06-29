/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecs.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.AwsConfig;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class EcsBGListenerUpdateRequest extends EcsCommandRequest {
  private String prodListenerArn;
  private String stageListenerArn;
  private boolean isUseSpecificListenerRuleArn;
  private String prodListenerRuleArn;
  private String stageListenerRuleArn;
  private String targetGroupArn1;
  private String targetGroupArn2;
  private String serviceName;
  private String serviceNameDownsized;
  private int serviceCountDownsized;
  private boolean rollback;
  private boolean downsizeOldService;
  private long downsizeOldServiceDelayInSecs;
  private boolean ecsBgDownsizeDelayEnabled;
  private String targetGroupForNewService;
  private String targetGroupForExistingService;
  private int serviceSteadyStateTimeout;

  @Builder
  public EcsBGListenerUpdateRequest(String commandName, String appId, String accountId, String activityId,
      String prodListenerArn, String stageListenerArn, String prodListenerRuleArn, String stageListenerRuleArn,
      String targetGroupArn1, String targetGroupArn2, String serviceName, String clusterName, String region,
      String serviceNameDownsized, int serviceCountDownsized, AwsConfig awsConfig, boolean rollback,
      boolean downsizeOldService, long downsizeOldServiceDelayInSecs, boolean ecsBgDownsizeDelayEnabled,
      boolean isUseSpecificListenerRuleArn, String targetGroupForNewService, String targetGroupForExistingService,
      int serviceSteadyStateTimeout, boolean timeoutErrorSupported) {
    super(accountId, appId, commandName, activityId, region, clusterName, awsConfig, EcsCommandType.LISTENER_UPDATE_BG,
        timeoutErrorSupported);
    this.prodListenerArn = prodListenerArn;
    this.stageListenerArn = stageListenerArn;
    this.prodListenerRuleArn = prodListenerRuleArn;
    this.stageListenerRuleArn = stageListenerRuleArn;
    this.targetGroupArn1 = targetGroupArn1;
    this.targetGroupArn2 = targetGroupArn2;
    this.serviceName = serviceName;
    this.serviceNameDownsized = serviceNameDownsized;
    this.serviceCountDownsized = serviceCountDownsized;
    this.rollback = rollback;
    this.targetGroupForNewService = targetGroupForNewService;
    this.targetGroupForExistingService = targetGroupForExistingService;
    this.downsizeOldService = downsizeOldService;
    this.downsizeOldServiceDelayInSecs = downsizeOldServiceDelayInSecs;
    this.ecsBgDownsizeDelayEnabled = ecsBgDownsizeDelayEnabled;
    this.isUseSpecificListenerRuleArn = isUseSpecificListenerRuleArn;
    this.serviceSteadyStateTimeout = serviceSteadyStateTimeout;
  }
}
