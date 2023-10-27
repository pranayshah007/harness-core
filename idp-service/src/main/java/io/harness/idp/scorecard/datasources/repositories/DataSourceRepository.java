/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface DataSourceRepository extends CrudRepository<DataSourceEntity, String>, DataSourceRepositoryCustom {
  Optional<DataSourceEntity> findByAccountIdentifierInAndIdentifier(Set<String> accountIdentifier, String identifier);
  List<DataSourceEntity> findAllByAccountIdentifierIn(Set<String> accountIdentifiers);
}
