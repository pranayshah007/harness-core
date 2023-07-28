/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName("NgConnectionStringsSweepingOutput")
@TypeAlias("ngConnectionStringsSweepingOutput")
@RecasterAlias("io.harness.cdng.azure.webapp.steps.NgConnectionStringsSweepingOutput")
public class NgConnectionStringsSweepingOutput implements ExecutionSweepingOutput {
  @NotNull StoreConfigWrapper store;
}
