/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.ModuleType.ModuleLifeCycleStage.ONBOARDED;
import static io.harness.ModuleType.ModuleLifeCycleStage.ONBOARDING_IN_PROGRESS;
import static io.harness.ModuleType.ModuleVisibility.INTERNAL;
import static io.harness.ModuleType.ModuleVisibility.PUBLIC;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(PL)
// When adding new module lifecycleStage field should be set to ONBOARDING_IN_PROGRESS unless all the services deploys
// these changes.
public enum ModuleType {
  // Public modules which have been onboarded
  @JsonProperty("CD") CD("Continuous Deployment", PUBLIC, ONBOARDED),
  @JsonProperty("CI") CI("Continuous Integration", PUBLIC, ONBOARDED),
  @JsonProperty("CV") @Deprecated CV("Continuous Verification", PUBLIC, ONBOARDED),
  @JsonProperty("CF") CF("Continuous Features", PUBLIC, ONBOARDED),
  @JsonProperty("CE") CE("Continuous Efficiency", PUBLIC, ONBOARDED),
  @JsonProperty("STO") STO("Security Testing Orchestration", PUBLIC, ONBOARDED),
  @JsonProperty("CHAOS") CHAOS("Chaos Engineering", PUBLIC, ONBOARDED),
  @JsonProperty("SRM") SRM("Service Reliability Management", PUBLIC, ONBOARDED),
  @JsonProperty("IACM") IACM("Infrastructure as Code Manager", PUBLIC, ONBOARDED),

  // Internal modules which have been onboarded
  @JsonProperty("CODE") CODE("Code", INTERNAL, ONBOARDED), // TODO: Remove internal flag once licensing is added.
  @JsonProperty("CORE") CORE("Core", INTERNAL, ONBOARDED),
  @JsonProperty("PMS") PMS("Pipelines", INTERNAL, ONBOARDED),
  @JsonProperty("TEMPLATESERVICE") TEMPLATESERVICE("TemplateService", INTERNAL, ONBOARDED),

  // Internal modules which have not been onboarded yet
  @JsonProperty("GOVERNANCE") GOVERNANCE("Governance", INTERNAL, ONBOARDING_IN_PROGRESS);
  String displayName;
  ModuleVisibility visibility;

  // Until workaround is implemented Ignore flag should be set to True for the first release of a new module
  ModuleLifeCycleStage lifecycleStage;

  enum ModuleVisibility { INTERNAL, PUBLIC }
  enum ModuleLifeCycleStage { ONBOARDING_IN_PROGRESS, ONBOARDED }

  ModuleType(String displayName, ModuleVisibility visibility, ModuleLifeCycleStage lifecycleStage) {
    this.displayName = displayName;
    this.visibility = visibility;
    this.lifecycleStage = lifecycleStage;
  }

  public static List<ModuleType> getModules() {
    List<ModuleType> moduleList = new ArrayList<>();
    for (ModuleType module : ModuleType.values()) {
      if (ModuleLifeCycleStage.ONBOARDED.equals(module.lifecycleStage)) {
        moduleList.add(module);
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
    return ModuleVisibility.INTERNAL.equals(visibility);
  }
}
