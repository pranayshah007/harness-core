/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.encryption.Scope.ORG;
import static io.harness.encryption.Scope.PROJECT;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotation.RecasterAlias;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Field;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "SecretRefData", description = "This entity contains the details of the Secret Referenced")
@RecasterAlias("io.harness.encryption.SecretRefData")
public class SecretRefData {
  private String identifier;
  private Scope scope;
  private char[] decryptedValue;
  public static final String SECRET_DELIMINITER = "\\.";
  public static final String SECRET_DOT_DELIMINITER = ".";

  public SecretRefData(String secretRefConfigString) {
    if (isBlank(secretRefConfigString)) {
      return;
    }
    String[] secretStringWithScopeSplitted = secretRefConfigString.split(SECRET_DELIMINITER, 2);
    if (secretStringWithScopeSplitted.length == 1) {
      this.identifier = secretStringWithScopeSplitted[0];
      this.scope = PROJECT;
    } else if (secretStringWithScopeSplitted.length == 2) {
      this.identifier = secretStringWithScopeSplitted[1];
      this.scope = IdentifierRefHelper.getScope(secretStringWithScopeSplitted[0]);
      if (this.scope == PROJECT) {
        // The user should not specify proj if it is a project level ref
        throw new InvalidRequestException("Invalid Secret Reference");
      }
    } else {
      throw new InvalidRequestException("Invalid Secret Reference");
    }
  }

  public SecretRefData(String identifier, Scope scope, char[] decryptedValue) {
    this.identifier = identifier;
    this.scope = scope;
    this.decryptedValue = decryptedValue == null ? null : decryptedValue.clone();
  }

  @JsonValue
  public String toSecretRefStringValue() {
    if (this.scope == null || this.identifier == null) {
      return null;
    }
    if (scope == PROJECT) {
      return identifier;
    }
    String scopeString = getScopeString(this.scope);
    return scopeString + SECRET_DOT_DELIMINITER + identifier;
  }

  private String getScopeString(Scope scope) {
    switch (scope) {
      case ACCOUNT:
        return ACCOUNT.getYamlRepresentation();
      case ORG:
        return ORG.getYamlRepresentation();
      case PROJECT:
        return PROJECT.getYamlRepresentation();
      default:
        throw new UnknownEnumTypeException("Scope", scope.toString());
    }
  }

  public boolean isNull() {
    try {
      for (Field f : getClass().getDeclaredFields()) {
        if (f.get(this) != null && !f.getName().equals("SECRET_DELIMINITER")
            && !f.getName().equals("SECRET_DOT_DELIMINITER")) {
          return false;
        }
      }
      return true;
    } catch (IllegalAccessException ex) {
      throw new UnexpectedException("Error while checking whether the secret ref is null");
    }
  }
}
