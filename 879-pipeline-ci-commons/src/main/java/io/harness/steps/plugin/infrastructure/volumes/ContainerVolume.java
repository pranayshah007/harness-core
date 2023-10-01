/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin.infrastructure.volumes;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true,
    include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = EmptyDirYaml.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmptyDirYaml.class, name = "EmptyDir")
  , @JsonSubTypes.Type(value = PersistentVolumeClaimYaml.class, name = "PersistentVolumeClaim"),
      @JsonSubTypes.Type(value = HostPathYaml.class, name = "HostPath")
})
@Deprecated
public interface ContainerVolume {
  @TypeAlias("volume_type")
  enum Type {
    @JsonProperty("EmptyDir") EMPTY_DIR("EmptyDir"),
    @JsonProperty("PersistentVolumeClaim") PERSISTENT_VOLUME_CLAIM("PersistentVolumeClaim"),
    @JsonProperty("HostPath") HOST_PATH("HostPath");

    private final String yamlName;

    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }
  ContainerVolume.Type getType();
}
