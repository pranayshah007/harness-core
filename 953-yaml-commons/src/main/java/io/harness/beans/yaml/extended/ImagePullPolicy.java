/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended;

import static io.harness.pms.yaml.YamlUtils.NULL_STR;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("imagePullPolicy")
@RecasterAlias("io.harness.beans.yaml.extended.ImagePullPolicy")
public enum ImagePullPolicy {
  @JsonProperty("Always") ALWAYS("Always"),
  @JsonProperty("Never") NEVER("Never"),
  @JsonProperty("IfNotPresent") IFNOTPRESENT("IfNotPresent");
  private final String yamlName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ImagePullPolicy getImagePullPolicy(@JsonProperty("pullPolicy") String yamlName) {
    if (Strings.isBlank(yamlName) || yamlName.equals(NULL_STR)) {
      return null;
    }
    for (ImagePullPolicy pullPolicy : ImagePullPolicy.values()) {
      if (pullPolicy.yamlName.equalsIgnoreCase(yamlName)) {
        return pullPolicy;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Image pull policy %s is invalid, valid values : %s", yamlName, Arrays.asList(ImagePullPolicy.values())));
  }

  ImagePullPolicy(String yamlName) {
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

  public static ImagePullPolicy fromString(final String s) {
    return ImagePullPolicy.getImagePullPolicy(s);
  }
}
