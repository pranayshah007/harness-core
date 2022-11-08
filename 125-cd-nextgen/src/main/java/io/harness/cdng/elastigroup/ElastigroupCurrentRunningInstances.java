/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@JsonTypeName("CurrentRunning")
@TypeAlias("ElastigroupCurrentRunningInstances")
@RecasterAlias("io.harness.cdng.elastigroup.ElastigroupCurrentRunningInstances")
public class ElastigroupCurrentRunningInstances implements ElastigroupInstancesSpec {
  @Override
  @JsonIgnore
  public ElastigroupInstancesType getType() {
    return ElastigroupInstancesType.CURRENT_RUNNING;
  }
}
