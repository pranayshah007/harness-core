/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RancherClusterElement implements ContextElement {
  private String uuid;
  private String clusterName;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.RANCHER_K8S_CLUSTER_CRITERIA;
  }

  @Override
  public String getUuid() {
    return this.uuid;
  }

  @Override
  public String getName() {
    return this.clusterName;
  }

  @Override
  public ContextElement cloneMin() {
    return new RancherClusterElement(this.uuid, this.clusterName);
  }
}
