package io.harness.steps.plugin.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.data.annotation.TypeAlias;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, defaultImpl = ContainerK8sInfra.class)
@JsonSubTypes({ @JsonSubTypes.Type(value = ContainerK8sInfra.class, name = "KubernetesDirect") })

public interface ContainerStepInfra {
  @TypeAlias("infrastructure_type")
  enum Type {
    @JsonProperty("KubernetesDirect") KUBERNETES_DIRECT("KubernetesDirect");
    private final String yamlName;

    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }
  @ApiModelProperty(allowableValues = "KubernetesDirect") Type getType();
}
