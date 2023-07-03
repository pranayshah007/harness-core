/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.info;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@JsonTypeName("SpotServerInstanceInfo")
@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo")
public class SpotServerInstanceInfo extends ServerInstanceInfo {
  private String infrastructureKey;
  private String ec2InstanceId;
  private String elastigroupId;
}
