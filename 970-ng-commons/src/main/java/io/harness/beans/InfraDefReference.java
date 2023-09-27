/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class InfraDefReference implements EntityReference {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String envIdentifier;
  String identifier;

  String repoIdentifier;
  String branch;
  Boolean isDefault;
  Scope scope;

  @Override
  public String getFullyQualifiedName() {
    String fqn = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier);
    return fqn + "/" + identifier;
  }

  @Override
  public Boolean isDefault() {
    return isDefault;
  }

  @Override
  public Scope getScope() {
    if (!isNull(this.scope)) {
      return this.scope;
    }
    return Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
