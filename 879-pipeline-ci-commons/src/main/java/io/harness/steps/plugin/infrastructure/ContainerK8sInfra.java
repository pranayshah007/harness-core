/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin.infrastructure;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("KubernetesDirect")
@TypeAlias("ContainerK8sInfra")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.plugin.infrastructure.ContainerK8sInfra")
public class ContainerK8sInfra implements ContainerStepInfra {
  @Builder.Default @NotNull @Getter private Type type = Type.KUBERNETES_DIRECT;
  @NotNull private ContainerInfraYamlSpec spec;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  // Todo(Sahil): Merge with Infrastructure
  public Infrastructure toCIInfra() {
    return K8sDirectInfraYaml.builder()
        .type(Infrastructure.Type.KUBERNETES_DIRECT)
        .spec(K8sDirectInfraYamlSpec.builder()
                  .annotations(spec.getAnnotations())
                  .containerSecurityContext(spec.getContainerSecurityContext())
                  .labels(spec.getLabels())
                  .os(spec.getOs())
                  .connectorRef(spec.getConnectorRef())
                  .harnessImageConnectorRef(spec.getHarnessImageConnectorRef())
                  .labels(spec.getLabels())
                  .runAsUser(spec.getRunAsUser())
                  .initTimeout(spec.getInitTimeout())
                  .namespace(spec.getNamespace())
                  .build())
        .build();
  }
}
