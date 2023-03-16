/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.infrastructuremapping;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InfrastructureMappingDTO;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.DX)
public interface InfrastructureMappingService {
  Optional<InfrastructureMappingDTO> getByInfrastructureMappingId(String infrastructureMappingId);

  Optional<InfrastructureMappingDTO> createNewOrReturnExistingInfrastructureMapping(
      InfrastructureMappingDTO infrastructureMappingDTO);

  List<InfrastructureMappingDTO> getAllByInfrastructureKey(String accountIdentifier, String infrastructureKey);

  /**
   * Deletes all infrastructure mappings linked to a particular harness project.
   * @param accountIdentifier  the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @return boolean to indicate if deletion was successful
   */
  @NotNull
  boolean deleteAllFromProj(
      @NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier);
}
