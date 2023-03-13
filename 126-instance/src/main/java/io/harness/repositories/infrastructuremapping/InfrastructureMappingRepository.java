/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.infrastructuremapping;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InfrastructureMapping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.DX)
public interface InfrastructureMappingRepository extends CrudRepository<InfrastructureMapping, String> {
  Optional<InfrastructureMapping> findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndInfrastructureKey(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String infrastructureKey);

  List<InfrastructureMapping> findAllByAccountIdentifierAndInfrastructureKey(
      String accountIdentifier, String infrastructureKey);

  long deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
