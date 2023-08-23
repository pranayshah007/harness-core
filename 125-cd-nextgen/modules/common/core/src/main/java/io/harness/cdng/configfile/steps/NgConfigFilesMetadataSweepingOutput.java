/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@JsonTypeName("NgConfigFilesMetadataSweepingOutput")
@TypeAlias("ngConfigFileMetadataSweepingOutput")
@RecasterAlias("io.harness.cdng.configfile.steps.NgConfigFilesMetadataSweepingOutput")
public class NgConfigFilesMetadataSweepingOutput implements ExecutionSweepingOutput {
  @NotNull List<ConfigFileWrapper> finalSvcConfigFiles;
  @NotNull String serviceIdentifier;
  String environmentIdentifier;
  Map<String, String> configFileLocation;
}
