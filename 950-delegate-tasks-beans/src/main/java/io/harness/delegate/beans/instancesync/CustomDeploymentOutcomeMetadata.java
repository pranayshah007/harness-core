/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@JsonTypeName("CustomDeploymentOutcomeMetadata")
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("CustomDeploymentOutcomeMetadata")
@RecasterAlias("io.harness.delegate.beans.instancesync.CustomDeploymentOutcomeMetadata")
public class CustomDeploymentOutcomeMetadata extends DeploymentOutcomeMetadata {
  String instanceFetchScript;
  Set<String> delegateSelectors;
}
