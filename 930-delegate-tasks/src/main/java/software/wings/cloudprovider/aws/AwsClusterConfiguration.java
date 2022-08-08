/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import software.wings.cloudprovider.ClusterConfiguration;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by anubhaw on 12/29/16.
 */
@Data
@NoArgsConstructor
@OwnedBy(CDP)
public class AwsClusterConfiguration extends ClusterConfiguration {
  private String serviceDefinition;
  private String launcherConfiguration;
  private String vpcZoneIdentifiers;
  private List<String> availabilityZones;
  private String autoScalingGroupName;

  @Builder
  public AwsClusterConfiguration(Integer size, String name, String serviceDefinition, String launcherConfiguration,
      String vpcZoneIdentifiers, List<String> availabilityZones, String autoScalingGroupName) {
    super(size, name);
    this.serviceDefinition = serviceDefinition;
    this.launcherConfiguration = launcherConfiguration;
    this.vpcZoneIdentifiers = vpcZoneIdentifiers;
    this.availabilityZones = availabilityZones;
    this.autoScalingGroupName = autoScalingGroupName;
  }
}
