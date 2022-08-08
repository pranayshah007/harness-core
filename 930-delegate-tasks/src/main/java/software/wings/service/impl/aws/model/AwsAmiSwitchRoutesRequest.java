/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.impl.aws.model.AwsAmiRequest.AwsAmiRequestType.EXECUTE_AMI_SWITCH_ROUTE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class AwsAmiSwitchRoutesRequest extends AwsAmiRequest {
  private String accountId;
  private String appId;
  private String activityId;
  private String commandName;
  private String oldAsgName;
  private List<String> primaryClassicLBs;
  private List<String> primaryTargetGroupARNs;
  private String newAsgName;
  private List<String> stageClassicLBs;
  private List<String> stageTargetGroupARNs;
  private int registrationTimeout;
  private boolean downscaleOldAsg;
  private AwsAmiPreDeploymentData preDeploymentData;
  boolean rollback;
  private List<String> baseScalingPolicyJSONs;
  private List<String> scheduledActionJSONs;

  @Builder
  public AwsAmiSwitchRoutesRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String accountId, String appId, String activityId, String commandName, String oldAsgName,
      List<String> primaryClassicLBs, List<String> primaryTargetGroupARNs, String newAsgName,
      List<String> stageClassicLBs, List<String> stageTargetGroupARNs, int registrationTimeout,
      AwsAmiPreDeploymentData preDeploymentData, boolean downscaleOldAsg, boolean rollback,
      List<String> baseScalingPolicyJSONs, boolean amiInServiceHealthyStateFFEnabled, List<String> scheduledActions,
      boolean amiAsgConfigCopyEnabled) {
    super(awsConfig, encryptionDetails, EXECUTE_AMI_SWITCH_ROUTE, region, amiInServiceHealthyStateFFEnabled,
        amiAsgConfigCopyEnabled);
    this.accountId = accountId;
    this.appId = appId;
    this.activityId = activityId;
    this.commandName = commandName;
    this.oldAsgName = oldAsgName;
    this.primaryClassicLBs = primaryClassicLBs;
    this.primaryTargetGroupARNs = primaryTargetGroupARNs;
    this.newAsgName = newAsgName;
    this.stageClassicLBs = stageClassicLBs;
    this.stageTargetGroupARNs = stageTargetGroupARNs;
    this.registrationTimeout = registrationTimeout;
    this.preDeploymentData = preDeploymentData;
    this.downscaleOldAsg = downscaleOldAsg;
    this.rollback = rollback;
    this.baseScalingPolicyJSONs = baseScalingPolicyJSONs;
    this.scheduledActionJSONs = scheduledActions;
  }
}
