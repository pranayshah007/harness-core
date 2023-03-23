/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.nodes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.SSCAEnforceStepInfo;
import io.harness.yaml.core.StepSpecType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static io.harness.annotations.dev.HarnessTeam.CI;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SSCAEnforce")
@TypeAlias("SSCAEnforceStepNode")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.nodes.SSCAEnforceStepNode")
public class SSCAEnforceStepNode extends CIAbstractStepNode {
    @JsonProperty("type")
    @NotNull SSCAEnforceStepNode.StepType type = StepType.SSCAEnforce;
    @NotNull
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    SSCAEnforceStepInfo sscsEnforceStepNodeInfo;

    @Override
    public String getType() {
        return CIStepInfoType.SSCAOrchestrate.getDisplayName();
    }

    @Override
    public StepSpecType getStepSpecType() {
        return sscsEnforceStepNodeInfo;
    }

    public enum StepType {
        SSCAEnforce(CIStepInfoType.SSCAEnforce.getDisplayName());
        @Getter
        String name;

        StepType(String name) {
            this.name = name;
        }
    }
}
