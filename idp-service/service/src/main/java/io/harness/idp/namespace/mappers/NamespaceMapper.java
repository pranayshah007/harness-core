/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class NamespaceMapper {
  public NamespaceInfo toDTO(NamespaceEntity namespaceNameEntity) {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(namespaceNameEntity.getId());
    namespaceInfo.setAccountIdentifier(namespaceNameEntity.getAccountIdentifier());
    return namespaceInfo;
  }

  public NamespaceEntity fromDTO(NamespaceInfo namespaceInfo) {
    return NamespaceEntity.builder()
        .accountIdentifier(namespaceInfo.getAccountIdentifier())
        .id(namespaceInfo.getNamespace())
        .build();
  }
}
