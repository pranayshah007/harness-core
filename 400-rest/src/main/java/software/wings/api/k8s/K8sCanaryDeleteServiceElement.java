/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName("K8sCanaryDeleteServiceElement")
@OwnedBy(CDP)
public class K8sCanaryDeleteServiceElement implements SweepingOutput {
  public static final String SWEEPING_OUTPUT_NAME = "K8sCanaryDeleteServiceElement";

  private boolean previousDeployedK8sCanary;

  @Override
  public String getType() {
    return "K8sCanaryDeleteServiceElement";
  }

  public boolean getPreviousDeployedK8sCanary() {
    return previousDeployedK8sCanary;
  }
}
