/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.beans;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS)
@TypeAlias("FetchLinkedAppsStepNode")
@OwnedBy(GITOPS)
@RecasterAlias("io.harness.cdng.gitops.beans.FetchLinkedAppsStepNode")
public class FetchLinkedAppsStepNode extends CdAbstractStepNode {
  @JsonProperty("type") @NotNull FetchLinkedAppsStepNode.StepType type = StepType.GitOpsFetchLinkedApps;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FetchLinkedAppsStepInfo fetchLinkedAppsStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return fetchLinkedAppsStepInfo;
  }

  enum StepType {
    GitOpsFetchLinkedApps(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
