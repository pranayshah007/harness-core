package io.harness.steps.plugin.infrastructure;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYamlSpec;
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
  @NotNull private K8sDirectInfraYamlSpec spec;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
}
