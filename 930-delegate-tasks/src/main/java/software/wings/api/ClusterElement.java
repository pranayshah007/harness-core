/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.context.ContextElementType.CLUSTER;

import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(_957_CG_BEANS)
public class ClusterElement implements ContextElement {
  private String uuid;
  private String name;
  private DeploymentType deploymentType;
  private String infraMappingId;

  @Override
  public ContextElementType getElementType() {
    return CLUSTER;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
