/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface DataPointsRepository extends CrudRepository<DataPointEntity, String>, DataPointRepositoryCustom {
  List<DataPointEntity> findAllByAccountIdentifierInAndDataSourceIdentifier(
      Set<String> accountIdentifiers, String dataSourceIdentifier);
  List<DataPointEntity> findByAccountIdentifierInAndDataSourceIdentifierAndIdentifierIn(
      Set<String> accountIdentifiers, String dataSourceIdentifier, List<String> identifiers);
  List<DataPointEntity> findByIdentifierIn(Set<String> identifiers);

  List<DataPointEntity> findAllByAccountIdentifierIn(Set<String> accountIdentifiers);

  Optional<DataPointEntity> findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
      Set<String> accountIdentifiers, String dataSourceIdentifier, String identifier);
}
