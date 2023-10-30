/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Optional;

public enum Scope {
  @JsonProperty("account") ACCOUNT("account"),
  @JsonProperty("org") ORG("org"),
  @JsonProperty("project") PROJECT("project"),
  @JsonProperty("unknown") UNKNOWN("unknown");
  // TODO: mark UNKNOWN as JsonIgnore if Scope ever becomes visible in swagger
  private final String yamlRepresentation;

  public String getYamlRepresentation() {
    return yamlRepresentation;
  }

  Scope(String scopeStr) {
    this.yamlRepresentation = scopeStr;
  }

  public static Scope fromString(String scopeStr) {
    Optional<Scope> scopeOptional =
        Arrays.stream(Scope.values()).filter(scope -> scope.yamlRepresentation.equalsIgnoreCase(scopeStr)).findFirst();
    if (scopeOptional.isPresent()) {
      return scopeOptional.get();
    }
    throw new IllegalArgumentException("No scope found for string: " + scopeStr);
  }

  public static Scope of(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Scope.UNKNOWN;
    }
    if (isEmpty(orgIdentifier)) {
      if (isEmpty(projectIdentifier)) {
        return Scope.ACCOUNT;
      } else {
        return Scope.UNKNOWN;
      }
    }
    if (isEmpty(projectIdentifier)) {
      return Scope.ORG;
    }
    return Scope.PROJECT;
  }
}
