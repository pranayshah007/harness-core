/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ScopeIdentifiers;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

import com.google.common.base.Strings;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ScopeIdentifierMapper {
  public Scope getScopeFromScopeIdentifiers(ScopeIdentifiers scopeIdentifiers) {
    return Scope.builder()
        .accountIdentifier(scopeIdentifiers.getAccountIdentifier())
        .orgIdentifier(scopeIdentifiers.getOrgIdentifier())
        .projectIdentifier(scopeIdentifiers.getProjectIdentifier())
        .build();
  }

  public ScopeIdentifiers getScopeIdentifiersFromScope(Scope scope) {
    return ScopeIdentifiers.newBuilder()
        .setAccountIdentifier(scope.getAccountIdentifier())
        .setOrgIdentifier(Strings.nullToEmpty(scope.getOrgIdentifier()))
        .setProjectIdentifier(Strings.nullToEmpty(scope.getProjectIdentifier()))
        .build();
  }

  public io.harness.encryption.Scope getEncryptionScopeFromScopeIdentifiers(ScopeIdentifiers scopeIdentifiers) {
    if (isNotEmpty(scopeIdentifiers.getProjectIdentifier())) {
      return io.harness.encryption.Scope.PROJECT;
    } else if (isNotEmpty(scopeIdentifiers.getOrgIdentifier())) {
      return io.harness.encryption.Scope.ORG;
    } else {
      return io.harness.encryption.Scope.ACCOUNT;
    }
  }
}
