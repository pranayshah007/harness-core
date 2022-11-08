/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ec2.service.helper;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
public class AWSEC2Details {
  private String instanceId;
  private String region;

  @Builder
  public AWSEC2Details(String instanceId, String region) {
    this.instanceId = instanceId;
    this.region = region;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AWSEC2Details) {
      if (((AWSEC2Details) o).instanceId.equals(instanceId) && ((AWSEC2Details) o).region.equals(region)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.instanceId);
  }
}
