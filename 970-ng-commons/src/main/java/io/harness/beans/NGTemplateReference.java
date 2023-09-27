/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.EntityReferenceHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;

import java.util.LinkedList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDC)
public class NGTemplateReference implements EntityReference {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;

  String versionLabel;
  String repoIdentifier;
  String branch;
  Boolean isDefault;
  Scope scope;

  @Override
  public String getFullyQualifiedName() {
    List<String> fqnList = new LinkedList<>();
    fqnList.add(accountIdentifier);
    if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      fqnList.add(orgIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      fqnList.add(projectIdentifier);
    }
    fqnList.add(identifier);
    if (EmptyPredicate.isNotEmpty(versionLabel)) {
      fqnList.add(versionLabel);
    }

    return EntityReferenceHelper.createFQN(fqnList);
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

  @Override
  public Scope getScope() {
    if (!isNull(this.scope)) {
      return this.scope;
    }
    return Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
