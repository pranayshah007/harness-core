/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface NamespaceRepository extends CrudRepository<NamespaceEntity, String>, NamespaceRepositoryCustom {
  Optional<NamespaceEntity> findByAccountIdentifier(String accountIdentifier);

  Optional<NamespaceEntity> findById(String id);

  List<NamespaceEntity> findAllByIsDeleted(boolean deleted);

  Optional<NamespaceEntity> findByAccountIdentifierAndIsDeleted(String accountIdentifier, boolean deleted);

  NamespaceEntity save(NamespaceEntity namespaceEntity);
}
