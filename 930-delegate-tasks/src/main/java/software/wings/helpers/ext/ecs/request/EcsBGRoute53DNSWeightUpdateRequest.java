/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecs.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.helpers.ext.ecs.request.EcsCommandRequest.EcsCommandType.ROUTE53_DNS_WEIGHT_UPDATE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.AwsConfig;
import software.wings.beans.container.AwsAutoScalarConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class EcsBGRoute53DNSWeightUpdateRequest extends EcsCommandRequest {
  private boolean rollback;
  private String serviceName;
  private String serviceNameDownsized;
  private int serviceCountDownsized;
  private boolean downsizeOldService;
  private int oldServiceWeight;
  private int newServiceWeight;
  private String parentRecordName;
  private String parentRecordHostedZoneId;
  private String oldServiceDiscoveryArn;
  private String newServiceDiscoveryArn;
  private List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs;
  private int timeout;
  private int ttl;

  @Builder
  public EcsBGRoute53DNSWeightUpdateRequest(String accountId, String appId, String commandName, String activityId,
      String region, String cluster, AwsConfig awsConfig, boolean rollback, String serviceName,
      String serviceNameDownsized, int serviceCountDownsized, boolean downsizeOldService, int oldServiceWeight,
      int newServiceWeight, String parentRecordName, String parentRecordHostedZoneId, String oldServiceDiscoveryArn,
      String newServiceDiscoveryArn, List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs, int timeout, int ttl,
      boolean timeoutErrorSupported) {
    super(accountId, appId, commandName, activityId, region, cluster, awsConfig, ROUTE53_DNS_WEIGHT_UPDATE,
        timeoutErrorSupported);
    this.rollback = rollback;
    this.serviceName = serviceName;
    this.serviceNameDownsized = serviceNameDownsized;
    this.serviceCountDownsized = serviceCountDownsized;
    this.downsizeOldService = downsizeOldService;
    this.oldServiceWeight = oldServiceWeight;
    this.newServiceWeight = newServiceWeight;
    this.parentRecordName = parentRecordName;
    this.parentRecordHostedZoneId = parentRecordHostedZoneId;
    this.oldServiceDiscoveryArn = oldServiceDiscoveryArn;
    this.newServiceDiscoveryArn = newServiceDiscoveryArn;
    this.previousAwsAutoScalarConfigs = previousAwsAutoScalarConfigs;
    this.timeout = timeout;
    this.ttl = ttl;
  }
}
