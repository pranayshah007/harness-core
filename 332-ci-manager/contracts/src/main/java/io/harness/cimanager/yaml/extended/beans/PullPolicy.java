/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.beans;

import io.harness.beans.yaml.extended.ImagePullPolicy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PullPolicy {
  @JsonProperty("always")
  ALWAYS("always") {
    @Override
    public ImagePullPolicy toImagePullPolicy() {
      return ImagePullPolicy.ALWAYS;
    }
  },
  @JsonProperty("never")
  NEVER("never") {
    @Override
    public ImagePullPolicy toImagePullPolicy() {
      return ImagePullPolicy.NEVER;
    }
  },
  @JsonProperty("if-not-exists")
  IF_NOT_EXISTS("if-not-exists") {
    @Override
    public ImagePullPolicy toImagePullPolicy() {
      return ImagePullPolicy.IFNOTPRESENT;
    }
  };

  private final String yamlName;

  PullPolicy(String yamlName) {
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

  public abstract ImagePullPolicy toImagePullPolicy();
}
