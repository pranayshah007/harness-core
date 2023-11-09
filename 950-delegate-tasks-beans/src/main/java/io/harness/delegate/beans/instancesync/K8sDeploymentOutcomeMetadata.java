/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("K8sDeploymentOutcomeMetadata")
@JsonTypeName("K8sDeploymentOutcomeMetadata")
@RecasterAlias("io.harness.delegate.beans.instancesync.K8sDeploymentOutcomeMetadata")
public class K8sDeploymentOutcomeMetadata extends DeploymentOutcomeMetadata {
  private boolean canary;
}
