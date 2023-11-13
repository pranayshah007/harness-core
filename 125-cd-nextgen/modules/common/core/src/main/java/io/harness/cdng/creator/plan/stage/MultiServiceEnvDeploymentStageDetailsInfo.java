/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(DeploymentStageTypeConstants.MULTI_SERVICE_ENVIRONMENT)
@TypeAlias("multiServiceEnvDeploymentStageDetailsInfo")
public class MultiServiceEnvDeploymentStageDetailsInfo implements DeploymentStageDetailsInfo {
  @Nullable private Set<String> envIdentifiers;
  @Nullable private Set<String> envNames;
  @Nullable private Set<String> infraIdentifiers;
  @Nullable private Set<String> infraNames;
  @Nullable private Set<String> serviceIdentifiers;
  @Nullable private Set<String> serviceNames;
  @Nullable private String envGroup;

  @Override
  public CDStageSummaryResponseDTO getFormattedStageSummary() {
    return null;
  }
}
