/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.infrastructuremapping;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.entities.InfrastructureMapping;
import io.harness.mappers.InfrastructureMappingMapper;
import io.harness.repositories.infrastructuremapping.InfrastructureMappingRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InfrastructureMappingServiceImpl implements InfrastructureMappingService {
  @Inject InfrastructureMappingRepository infrastructureMappingRepository;

  public Optional<InfrastructureMappingDTO> getByInfrastructureMappingId(String infrastructureMappingId) {
    Optional<InfrastructureMapping> infrastructureMappingOptional =
        infrastructureMappingRepository.findById(infrastructureMappingId);
    return infrastructureMappingOptional.map(InfrastructureMappingMapper::toDTO);
  }

  @Override
  public Optional<InfrastructureMappingDTO> createNewOrReturnExistingInfrastructureMapping(
      InfrastructureMappingDTO infrastructureMappingDTO) {
    try {
      InfrastructureMapping infrastructureMapping =
          infrastructureMappingRepository.save(InfrastructureMappingMapper.toEntity(infrastructureMappingDTO));
      return Optional.of(InfrastructureMappingMapper.toDTO(infrastructureMapping));
    } catch (DuplicateKeyException duplicateKeyException) {
      log.warn("Duplicate key error while inserting infrastructure mapping for infrastructure key : {}",
          infrastructureMappingDTO.getInfrastructureKey());
      Optional<InfrastructureMapping> infrastructureMappingOptional =
          infrastructureMappingRepository
              .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndInfrastructureKey(
                  infrastructureMappingDTO.getAccountIdentifier(), infrastructureMappingDTO.getOrgIdentifier(),
                  infrastructureMappingDTO.getProjectIdentifier(), infrastructureMappingDTO.getInfrastructureKey());
      return infrastructureMappingOptional.map(InfrastructureMappingMapper::toDTO);
    }
  }

  @Override
  public List<InfrastructureMappingDTO> getAllByInfrastructureKey(String accountIdentifier, String infrastructureKey) {
    List<InfrastructureMapping> infrastructureMappings =
        infrastructureMappingRepository.findAllByAccountIdentifierAndInfrastructureKey(
            accountIdentifier, infrastructureKey);
    return infrastructureMappings.stream().map(InfrastructureMappingMapper::toDTO).collect(Collectors.toList());
  }

  @Override
  public boolean deleteAllFromProj(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountIdentifier), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");
    try {
      infrastructureMappingRepository.deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier);
    } catch (Exception e) {
      log.error("Error while deleting infrastructure mappings present in a project {}, org {}, account {}",
          projectIdentifier, orgIdentifier, accountIdentifier);
      return false;
    }
    return true;
  }
}
