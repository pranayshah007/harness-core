/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.beans.CVNGAbstractStepNode;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.VERIFY)
@OwnedBy(HarnessTeam.CV)
public class CVVerifyStepNode extends CVNGAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.Verify;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CVNGStepInfo verifyStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.VERIFY;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return verifyStepInfo;
  }

  enum StepType {
    Verify(StepSpecTypeConstants.VERIFY);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
