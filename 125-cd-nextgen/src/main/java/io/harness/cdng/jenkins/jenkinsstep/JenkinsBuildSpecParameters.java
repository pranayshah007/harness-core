/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins.jenkinsstep;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("jenkinsBuildSpecParameters")
@RecasterAlias("JenkinsBuildSpecParameters")
public class JenkinsBuildSpecParameters implements SpecParameters {
  @NotEmpty ParameterField<String> connectorRef;
  @NotEmpty ParameterField<String> jobName;
  Map<String, ParameterField<String>> fields;
  boolean unstableStatusAsSuccess;
  boolean useConnectorUrlForJobExecution;
  private Map<String, String> filePathsForAssertion;
  private String queuedBuildUrl;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  ParameterField<String> consoleLogPollFrequency;
}
