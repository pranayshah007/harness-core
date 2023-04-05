/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PIPELINE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true,
    include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = NoopStepGroupInfra.class)
public interface StepGroupInfra {
  @TypeAlias("infrastructure_type")
  enum Type {
    @JsonProperty("KubernetesDirect") KUBERNETES_DIRECT("KubernetesDirect"),
    @JsonProperty("Delegate") DELEGATE("Delegate"),
    @JsonProperty("Noop") NO_OP("Noop");
    private final String yamlName;

    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }

  @ApiModelProperty(allowableValues = "KubernetesDirect, Delegate, Noop") Type getType();
}
