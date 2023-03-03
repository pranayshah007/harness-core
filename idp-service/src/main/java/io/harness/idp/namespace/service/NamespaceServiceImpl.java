/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.service;

import static java.lang.String.format;

import io.harness.exception.InvalidRequestException;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import java.util.Optional;
import javax.inject.Inject;

public class NamespaceServiceImpl implements NamespaceService {
  @Inject private NamespaceRepository namespaceRepository;
  private static final String IDP_NOT_ENABLED = "IDP has not been set up for account [%s]";
  private static final String IDP_NAMESPACE_NOT_LINKED = "Namespace - [%s] is not linked to any account";

  @Override
  public NamespaceInfo getNamespaceForAccountIdentifier(String accountId) {
    Optional<NamespaceEntity> namespaceName = namespaceRepository.findByAccountIdentifier(accountId);
    if (namespaceName.isEmpty()) {
      throw new InvalidRequestException(format(IDP_NOT_ENABLED, accountId));
    }
    return namespaceName.map(NamespaceMapper::toDTO).get();
  }

  @Override
  public NamespaceInfo getAccountIdForNamespace(String namespace) {
    Optional<NamespaceEntity> namespaceName = namespaceRepository.findById(namespace);
    if (namespaceName.isEmpty()) {
      throw new InvalidRequestException(format(IDP_NAMESPACE_NOT_LINKED, namespace));
    }
    return namespaceName.map(NamespaceMapper::toDTO).get();
  }

  @Override
  public NamespaceEntity saveAccountIdNamespace(String accountId) {
    NamespaceEntity dataToInsert = NamespaceEntity.builder().accountIdentifier(accountId).build();
    NamespaceEntity insertedData = namespaceRepository.save(dataToInsert);
    return insertedData;
  }
}
