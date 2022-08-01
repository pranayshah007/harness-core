/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment.pod.container;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import org.springframework.data.annotation.TypeAlias;

/**
 * Type of each container inside the pod for running CI job
 */

@OwnedBy(HarnessTeam.CI)
@TypeAlias("containerType")
@RecasterAlias("io.harness.beans.environment.pod.container.ContainerType")
public enum ContainerType {
  STEP_EXECUTOR(ContainerSource.BUILD_JOB);
  ContainerSource containerSource;

  ContainerType(ContainerSource containerSource) {
    this.containerSource = containerSource;
  }
}
