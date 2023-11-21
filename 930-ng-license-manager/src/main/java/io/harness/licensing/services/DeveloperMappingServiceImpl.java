/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.licensing.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.beans.modules.DeveloperMappingDTO;
import io.harness.licensing.entities.developer.DeveloperMapping;
import io.harness.licensing.mappers.DeveloperMappingObjectConverter;
import io.harness.repositories.DeveloperMappingRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.GTM)
@Slf4j
public class DeveloperMappingServiceImpl implements DeveloperMappingService {
  private final DeveloperMappingRepository developerMappingRepository;
  private final DeveloperMappingObjectConverter developerMappingObjectConverter;

  @Inject
  public DeveloperMappingServiceImpl(DeveloperMappingRepository developerMappingRepository,
      DeveloperMappingObjectConverter developerMappingObjectConverter) {
    this.developerMappingRepository = developerMappingRepository;
    this.developerMappingObjectConverter = developerMappingObjectConverter;
  }

  @Override
  public List<DeveloperMappingDTO> getAccountLevelDeveloperMapping(String accountIdentifier) {
    List<DeveloperMapping> developerMappings = developerMappingRepository.findByAccountIdentifier(accountIdentifier);
    return developerMappings.stream().map(developerMappingObjectConverter::toDTO).collect(Collectors.toList());
  }

  @Override
  public DeveloperMappingDTO createAccountLevelDeveloperMapping(
      String accountIdentifier, DeveloperMappingDTO developerMappingDTO) {
    validateDTO(accountIdentifier, developerMappingDTO);
    DeveloperMapping developerMapping = developerMappingObjectConverter.toEntity(developerMappingDTO);
    DeveloperMapping savedDeveloperMapping = developerMappingRepository.save(developerMapping);
    return developerMappingObjectConverter.toDTO(savedDeveloperMapping);
  }

  private void validateDTO(String accountIdentifier, DeveloperMappingDTO developerMappingDTO) {
    if (!accountIdentifier.equals(developerMappingDTO.getAccountIdentifier())) {
      String errorMessage =
          String.format("Account Identifier: [%s] did not match with the developer mapping DTO account identifier [%s]",
              accountIdentifier, developerMappingDTO.getAccountIdentifier());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    List<DeveloperMapping> existingDeveloperMapping =
        developerMappingRepository.findByAccountIdentifierAndModuleTypeAndSecondaryEntitlement(
            accountIdentifier, developerMappingDTO.getModuleType(), developerMappingDTO.getSecondaryEntitlement());
    if (!existingDeveloperMapping.isEmpty()) {
      throw new DuplicateFieldException(String.format(
          "Developer Mapping with accountIdentifier [%s], moduleType [%s] and secondary entitlement [%s] already exists",
          accountIdentifier, developerMappingDTO.getModuleType(), developerMappingDTO.getSecondaryEntitlement()));
    }
  }
}
