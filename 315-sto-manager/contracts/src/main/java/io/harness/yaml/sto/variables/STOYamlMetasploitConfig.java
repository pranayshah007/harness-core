/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.sto.variables;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("stoYamlMetasploitConfig")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlMetasploitConfig")
public enum STOYamlMetasploitConfig implements STOYamlConfig {
  @JsonProperty("default") DEFAULT("default"),
  @JsonProperty("metasploit-weak-ssh") WEAK_SSH("metasploit-weak-ssh"),
  @JsonProperty("metasploit-openssl-heartbleed") OPENSSL_HEARTBLEED("metasploit-openssl-heartbleed");
  private final String yamlName;

  STOYamlMetasploitConfig(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlMetasploitConfig getValue(@JsonProperty("config") String yamlName) {
    for (STOYamlMetasploitConfig value : STOYamlMetasploitConfig.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlMetasploitConfig fromString(final String s) {
    return STOYamlMetasploitConfig.getValue(s);
  }
}
