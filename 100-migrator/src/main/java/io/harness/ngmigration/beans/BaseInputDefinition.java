/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseInputDefinition {
  private boolean required;
  private boolean multiple;
  private List<PossibleValue> possibleValues;
  private String defaultValue;
  private boolean editable;
  private String label;
  private String cgValue;
  private boolean highlight;
  private String description;

  public static BaseInputDefinition buildIdentifier(String defaultValue) {
    return BaseInputDefinition.builder()
        .label("Identifier")
        .multiple(false)
        .cgValue(null)
        .defaultValue(defaultValue)
        .editable(true)
        .build();
  }

  public static BaseInputDefinition buildName(String defaultValue) {
    return BaseInputDefinition.builder()
        .label("Name")
        .multiple(false)
        .cgValue(null)
        .defaultValue(defaultValue)
        .editable(true)
        .build();
  }

  public static BaseInputDefinition buildScope(Scope defaultValue) {
    return BaseInputDefinition.builder()
        .label("Scope")
        .multiple(false)
        .cgValue(null)
        .defaultValue(defaultValue.name())
        .editable(true)
        .possibleValues(Lists.newArrayList(Scope.ACCOUNT, Scope.ORG, Scope.PROJECT)
                            .stream()
                            .map(scope -> PossibleValue.builder().label(scope.name()).value(scope.name()).build())
                            .collect(Collectors.toList()))
        .build();
  }
}
