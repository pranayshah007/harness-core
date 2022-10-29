/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(InstancesSpecTypeConstants.FIXED)
@TypeAlias("fixedInstancesSpecYaml")
@RecasterAlias("io.harness.cdng.elastigroup.FixedInstancesSpecYaml")
public class FixedInstancesSpecYaml extends InstancesSpecAbstractYaml {
  @JsonProperty("type")
  @NotNull
  FixedInstancesSpecYaml.InstanceType type =
          InstanceType.Fixed;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  FixedInstancesSpec fixedInstancesSpec;
  @Override
  public String getType() {
    return InstancesSpecTypeConstants.FIXED;
  }

  enum InstanceType {
    Fixed(InstancesSpecTypeConstants.FIXED);
    @Getter String name;
    InstanceType(String name) {
      this.name = name;
    }
  }
}
