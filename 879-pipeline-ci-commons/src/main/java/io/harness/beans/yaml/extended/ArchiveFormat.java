/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("archiveFormat")
@RecasterAlias("io.harness.beans.yaml.extended.ArchiveFormat")
public enum ArchiveFormat {
  @JsonProperty("Tar") TAR("tar"),
  @JsonProperty("Gzip") GZIP("gzip");
  private final String yamlName;
  @ApiModelProperty(hidden = true) String uuid;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ArchiveFormat getArchiveFormat(@JsonProperty("archiveFormat") String yamlName) {
    for (ArchiveFormat archiveFormat : ArchiveFormat.values()) {
      if (archiveFormat.yamlName.equalsIgnoreCase(yamlName)) {
        return archiveFormat;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  ArchiveFormat(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static ArchiveFormat fromString(final String s) {
    return ArchiveFormat.getArchiveFormat(s);
  }
}
