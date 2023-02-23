/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import java.util.Optional;
import javax.inject.Inject;

public class NamespaceServiceImpl implements NamespaceService {
  @Inject private NamespaceRepository namespaceRepository;

  @Override
  public Optional<NamespaceInfo> getNamespaceForAccountIdentifier(String accountId) {
    Optional<NamespaceEntity> namespaceName = namespaceRepository.findByAccountIdentifier(accountId);
    return namespaceName.map(NamespaceMapper::toDTO);
  }

  @Override
  public Optional<NamespaceInfo> getAccountIdForNamespace(String namespace) {
    Optional<NamespaceEntity> namespaceName = namespaceRepository.findById(namespace);
    return namespaceName.map(NamespaceMapper::toDTO);
  }

  @Override
  public NamespaceEntity saveAccountIdNamespace(String accountId) {
    NamespaceEntity dataToInsert = NamespaceEntity.builder().accountIdentifier(accountId).build();
    NamespaceEntity insertedData = namespaceRepository.save(dataToInsert);
    return insertedData;
  }
}
