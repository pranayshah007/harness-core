/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.dto.Namespace;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class NamespaceMapper {
  public Namespace toDTO(NamespaceEntity namespaceNameEntity) {
    return Namespace.builder()
        .accountIdentifier(namespaceNameEntity.getAccountIdentifier())
        .namespace(namespaceNameEntity.getId())
        .build();
  }

  public NamespaceEntity fromDTO(Namespace namespace) {
    return NamespaceEntity.builder()
        .accountIdentifier(namespace.getAccountIdentifier())
        .id(namespace.getNamespace())
        .build();
  }
}
