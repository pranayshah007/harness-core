/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.timeout.Timeout;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("customApprovalSpecParameters")
@RecasterAlias("io.harness.steps.approval.step.custom.CustomApprovalSpecParameters")
public class CustomApprovalSpecParameters implements SpecParameters {
  @NotNull ShellType shellType;
  @NotNull ShellScriptSourceWrapper source;
  @NotNull ParameterField<Timeout> retryInterval;
  Map<String, Object> outputVariables;
  Set<String> secretOutputVariables;
  Map<String, Object> environmentVariables;
  @NotNull CriteriaSpecWrapper approvalCriteria;
  CriteriaSpecWrapper rejectionCriteria;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  ParameterField<Timeout> scriptTimeout;

  @Override
  public SpecParameters getViewJsonObject() {
    // omit secretOutputVars since they should not be visible to users
    return CustomApprovalSpecParameters.builder()
        .retryInterval(this.retryInterval)
        .outputVariables(this.outputVariables)
        .environmentVariables(this.environmentVariables)
        .shellType(this.shellType)
        .source(this.source)
        .delegateSelectors(this.delegateSelectors)
        .approvalCriteria(this.approvalCriteria)
        .rejectionCriteria(this.rejectionCriteria)
        .scriptTimeout(this.scriptTimeout)
        .build();
  }

  @Override
  public List<String> stepInputsKeyExclude() {
    return new LinkedList<>(Arrays.asList("specConfig.secretOutputVariables"));
  }
}
