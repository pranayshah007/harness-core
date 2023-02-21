/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "IdentifierRefKeys")
@OwnedBy(HarnessTeam.PIPELINE)
public class IdentifierRef implements EntityReference {
  Scope scope;
  String identifier;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  Map<String, String> metadata;
  String repoIdentifier;
  String branch;
  Boolean isDefault;

  @Override
  public String getFullyQualifiedName() {
    return FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  public String getFullyQualifiedScopeIdentifier() {
    return FullyQualifiedIdentifierHelper.getFullyQualifiedScope(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  public String buildScopedIdentifier() {
    switch (scope) {
      case ACCOUNT:
        return "account." + identifier;
      case ORG:
        return "org." + identifier;
      case PROJECT:
        return identifier;
      default:
        return "";
    }
  }

  @Override
  public Boolean isDefault() {
    return isDefault;
  }

  @Override
  public void setBranch(String branch) {
    this.branch = branch;
  }

  @Override
  public void setRepoIdentifier(String repoIdentifier) {
    this.repoIdentifier = repoIdentifier;
  }

  @Override
  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }
}
