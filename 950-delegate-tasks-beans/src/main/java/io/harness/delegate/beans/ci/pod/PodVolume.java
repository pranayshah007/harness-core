/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true,
    include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = EmptyDirVolume.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = EmptyDirVolume.class, name = "EmptyDir")
  , @JsonSubTypes.Type(value = HostPathVolume.class, name = "HostPath"),
      @JsonSubTypes.Type(value = PVCVolume.class, name = "PersistentVolumeClaim")
})
public interface PodVolume {
  @TypeAlias("volume_type")
  enum Type {
    @JsonProperty("EmptyDir") EMPTY_DIR("EmptyDir"),
    @JsonProperty("PersistentVolumeClaim") PVC("PersistentVolumeClaim"),
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
  PodVolume.Type getType();
}
