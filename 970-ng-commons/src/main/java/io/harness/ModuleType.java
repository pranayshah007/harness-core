/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(PL)
public enum ModuleType {
  @JsonProperty("CD") CD("Continuous Deployment"),
  @JsonProperty("CI") CI("Continuous Integration"),
  @JsonProperty("CV") CV("Continuous Verification"),
  @JsonProperty("CF") CF("Continuous Features"),
  @JsonProperty("CE") CE("Continuous Efficiency"),
  @JsonProperty("STO") STO("Security Testing Orchestration"),

  // Internal
  @JsonProperty("CORE") CORE("Core", true, false),
  @JsonProperty("PMS") PMS("Pipelines", true, false),
  @JsonProperty("TEMPLATESERVICE") TEMPLATESERVICE("TemplateService", true, false),
  @JsonProperty("GOVERNANCE") GOVERNANCE("Governance", true, true),

  // TODO (prashant) : Setting this internal and ignored for now change when chaos integrates with license
  @JsonProperty("CHAOS") CHAOS("Chaos Engineering", true, true);

  String displayName;
  boolean internal;

  // Until workaround is implemented Ignore flag should be set to True for the first release of a new module
  boolean ignore;

  ModuleType(String displayName) {
    this(displayName, false, false);
  }

  ModuleType(String displayName, boolean internal, boolean ignore) {
    this.displayName = displayName;
    this.internal = internal;
    this.ignore = ignore;
  }

  public static List<ModuleType> getModules() {
    List<ModuleType> moduleList = new ArrayList<>();
    for (ModuleType moduleEnum : ModuleType.values()) {
      if (!moduleEnum.ignore) {
        moduleList.add(moduleEnum);
      }
    }

    return moduleList;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ModuleType fromString(String moduleType) {
    for (ModuleType moduleEnum : ModuleType.values()) {
      if (moduleEnum.name().equalsIgnoreCase(moduleType)) {
        return moduleEnum;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + moduleType);
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isInternal() {
    return internal;
  }
}
