/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.impl.aws.model.AwsAmiRequest.AwsAmiRequestType.EXECUTE_AMI_SERVICE_TRAFFIC_SHIFT_ALB_SETUP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
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
public class AwsAmiServiceTrafficShiftAlbSetupRequest extends AwsAmiRequest {
  private String accountId;
  private String appId;
  private String activityId;
  private String commandName;
  private String infraMappingAsgName;
  private String infraMappingId;
  private String newAsgNamePrefix;
  private Integer minInstances;
  private Integer maxInstances;
  private Integer desiredInstances;
  private Integer autoScalingSteadyStateTimeout;
  private String artifactRevision;
  private boolean useCurrentRunningCount;
  private List<LbDetailsForAlbTrafficShift> lbDetails;
  private String userData;

  @Builder
  public AwsAmiServiceTrafficShiftAlbSetupRequest(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String infraMappingAsgName, String infraMappingId, String newAsgNamePrefix, Integer maxInstances,
      Integer autoScalingSteadyStateTimeout, String artifactRevision, String accountId, String appId, String activityId,
      String commandName, boolean useCurrentRunningCount, Integer desiredInstances, Integer minInstances,
      List<LbDetailsForAlbTrafficShift> lbDetails, String userData, boolean amiInServiceHealthyStateFFEnabled,
      boolean amiAsgConfigCopyEnabled) {
    super(awsConfig, encryptionDetails, EXECUTE_AMI_SERVICE_TRAFFIC_SHIFT_ALB_SETUP, region,
        amiInServiceHealthyStateFFEnabled, amiAsgConfigCopyEnabled);
    this.appId = appId;
    this.activityId = activityId;
    this.useCurrentRunningCount = useCurrentRunningCount;
    this.infraMappingAsgName = infraMappingAsgName;
    this.infraMappingId = infraMappingId;
    this.artifactRevision = artifactRevision;
    this.newAsgNamePrefix = newAsgNamePrefix;
    this.maxInstances = maxInstances;
    this.autoScalingSteadyStateTimeout = autoScalingSteadyStateTimeout;
    this.commandName = commandName;
    this.desiredInstances = desiredInstances;
    this.minInstances = minInstances;
    this.lbDetails = lbDetails;
    this.accountId = accountId;
    this.userData = userData;
  }
}
