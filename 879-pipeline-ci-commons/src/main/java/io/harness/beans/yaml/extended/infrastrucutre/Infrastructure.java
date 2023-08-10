/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true,
    include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = UseFromStageInfraYaml.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8sDirectInfraYaml.class, name = "KubernetesDirect")
  , @JsonSubTypes.Type(value = UseFromStageInfraYaml.class, name = "UseFromStage"),
      @JsonSubTypes.Type(value = VmInfraYaml.class, name = "VM"),
      @JsonSubTypes.Type(value = HostedVmInfraYaml.class, name = "HostedVm"),
      @JsonSubTypes.Type(value = DockerInfraYaml.class, name = "DOCKER")
})

public interface Infrastructure {
  @TypeAlias("infrastructure_type")
  enum Type {
    @JsonProperty("KubernetesDirect") KUBERNETES_DIRECT("KubernetesDirect"),
    @JsonProperty("UseFromStage") USE_FROM_STAGE("UseFromStage"),
    @JsonProperty("VM") VM("VM"),
    @JsonProperty("HostedVm") HOSTED_VM("HostedVm"),
    @JsonProperty("DOCKER") DOCKER("DOCKER");

    private final String yamlName;

    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }
  @ApiModelProperty(allowableValues = "KubernetesDirect, UseFromStage, VM, KubernetesHosted") Type getType();
}
