/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.spec.server.ng.model;

/**
 * Type of Modules
 **/
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * Type of Modules
 */
public enum ModuleType {
  CD("CD"),
  CI("CI"),
  CV("CV"),
  CF("CF"),
  CE("CE"),
  STO("STO"),
  CORE("CORE"),
  PMS("PMS"),
  TEMPLATESERVICE("TEMPLATESERVICE"),
  GOVERNANCE("GOVERNANCE");

  private String value;

  ModuleType(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ModuleType fromValue(String text) {
    for (ModuleType b : ModuleType.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}
