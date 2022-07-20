/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment.pod.container;

import io.harness.annotation.RecasterAlias;
import io.harness.k8s.model.ImageDetails;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

/**
 * Stores connector identifier to fetch latest image from connector and populate imageDetails.
 */

@Data
@Builder
@TypeAlias("containerImageDetails")
@RecasterAlias("io.harness.beans.environment.pod.container.ContainerImageDetails")
public class ContainerImageDetails {
  private ImageDetails imageDetails;
  private String connectorIdentifier;
}
