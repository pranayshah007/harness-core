/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.beans;

import io.harness.beans.yaml.extended.TIBuildTool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BuildTool {
  @JsonProperty("maven")
  MAVEN("maven") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.MAVEN;
    }
  },
  @JsonProperty("bazel")
  BAZEL("bazel") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.BAZEL;
    }
  },
  @JsonProperty("gradle")
  GRADLE("gradle") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.GRADLE;
    }
  },
  @JsonProperty("dotnet")
  DOTNET("dotnet") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.DOTNET;
    }
  },
  @JsonProperty("nunit_console")
  NUNIT_CONSOLE("nunit_console") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.NUNITCONSOLE;
    }
  },
  @JsonProperty("sbt")
  SBT("sbt") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.SBT;
    }
  },
  @JsonProperty("pytest")
  PYTEST("pytest") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.PYTEST;
    }
  },
  @JsonProperty("unittest")
  UNITTEST("unittest") {
    @Override
    public TIBuildTool toTIBuildTool() {
      return TIBuildTool.UNITTEST;
    }
  };

  private final String yamlName;

  BuildTool(String yamlName) {
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

  public abstract TIBuildTool toTIBuildTool();
}
