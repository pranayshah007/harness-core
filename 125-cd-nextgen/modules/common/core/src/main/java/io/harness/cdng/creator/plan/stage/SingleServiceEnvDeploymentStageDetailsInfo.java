/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(DeploymentStageTypeConstants.SINGLE_SERVICE_ENVIRONMENT)
@TypeAlias("singleServiceEnvDeploymentStageDetailsInfo")
public class SingleServiceEnvDeploymentStageDetailsInfo implements DeploymentStageDetailsInfo {
  @Nullable private String envIdentifier;
  @Nullable private String envName;
  @Nullable private String infraIdentifier;
  @Nullable private String infraName;
  @Nullable private String serviceIdentifier;
  @Nullable private String serviceName;
  public static final String NOT_AVAILABLE = "NA";

  @Override
  public CDStageSummaryResponseDTO getFormattedStageSummary() {
    String environment = StringUtils.isBlank(envName) ? envIdentifier : envName;
    String service = StringUtils.isBlank(serviceName) ? serviceIdentifier : serviceName;
    String infra = StringUtils.isBlank(infraName) ? infraIdentifier : infraName;

    return CDStageSummaryResponseDTO.builder()
        .service(identityOrElseNAStringIfBlank(service))
        .infra(identityOrElseNAStringIfBlank(infra))
        .environment(identityOrElseNAStringIfBlank(environment))
        .build();
  }

  public static String identityOrElseNAStringIfBlank(String value) {
    return StringUtils.isNotBlank(value) ? value : NOT_AVAILABLE;
  }
}
