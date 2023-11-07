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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
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
  public static final String SERVICE = "Service";
  public static final String ENVIRONMENT = "Environment";
  public static final String INFRA_DEFINITION = "Infrastructure Definition";
  public static final String ROW_FORMAT = "%s%s%s";

  @Override
  public String getFormattedStageSummary(@NotNull String rowDelimiter, @NotNull String keyValueDelimiter) {
    List<String> summaryRows = new ArrayList<>();
    String environment = StringUtils.isBlank(envName) ? envIdentifier : envName;
    String service = StringUtils.isBlank(serviceName) ? serviceIdentifier : serviceName;
    String infra = StringUtils.isBlank(infraName) ? infraIdentifier : infraName;

    if (StringUtils.isNotBlank(service)) {
      summaryRows.add(String.format(ROW_FORMAT, SERVICE, keyValueDelimiter, service));
    } else {
      summaryRows.add(String.format(ROW_FORMAT, SERVICE, keyValueDelimiter, NOT_AVAILABLE));
    }

    if (StringUtils.isNotBlank(environment)) {
      summaryRows.add(String.format(ROW_FORMAT, ENVIRONMENT, keyValueDelimiter, environment));
    } else {
      summaryRows.add(String.format(ROW_FORMAT, ENVIRONMENT, keyValueDelimiter, NOT_AVAILABLE));
    }

    if (StringUtils.isNotBlank(infra)) {
      summaryRows.add(String.format(ROW_FORMAT, INFRA_DEFINITION, keyValueDelimiter, infra));
    } else {
      summaryRows.add(String.format(ROW_FORMAT, INFRA_DEFINITION, keyValueDelimiter, NOT_AVAILABLE));
    }
    return Joiner.on(rowDelimiter).join(summaryRows);
  }
}
