/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.releasedetailsmapping;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.ReleaseDetailsMappingDTO;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.mappers.ReleaseDetailsMappingMapper;
import io.harness.repositories.releasedetailsmapping.ReleaseDetailsMappingRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ReleaseDetailsMappingServiceImpl implements ReleaseDetailsMappingService {
  @Inject ReleaseDetailsMappingRepository releaseDetailsMappingRepository;

  @Override
  public Optional<ReleaseDetailsMappingDTO> createNewOrReturnExistingReleaseDetailsMapping(
      ReleaseDetailsMappingDTO releaseDetailsMappingDTO) {
    if (isEmpty(releaseDetailsMappingDTO.getReleaseKey())) {
      return Optional.empty();
    }
    try {
      ReleaseDetailsMapping releaseDetailsMapping =
          releaseDetailsMappingRepository.save(ReleaseDetailsMappingMapper.toEntity(releaseDetailsMappingDTO));
      return Optional.of(ReleaseDetailsMappingMapper.toDTO(releaseDetailsMapping));
    } catch (DuplicateKeyException duplicateKeyException) {
      log.warn("Duplicate key error while inserting release details mapping for releaseKey: {}",
          releaseDetailsMappingDTO.getReleaseKey());
      Optional<ReleaseDetailsMapping> releaseDetailsMappingOptional =
          releaseDetailsMappingRepository
              .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndReleaseKeyAndInfraKey(
                  releaseDetailsMappingDTO.getAccountIdentifier(), releaseDetailsMappingDTO.getOrgIdentifier(),
                  releaseDetailsMappingDTO.getProjectIdentifier(), releaseDetailsMappingDTO.getReleaseKey(),
                  releaseDetailsMappingDTO.getInfraKey());
      return releaseDetailsMappingOptional.map(ReleaseDetailsMappingMapper::toDTO);
    }
  }

  @Override
  public boolean deleteAllFromProj(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountIdentifier), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");
    try {
      releaseDetailsMappingRepository.deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier);
    } catch (Exception e) {
      log.error("Error while deleting infrastructure mappings present in a project {}, org {}, account {}",
          projectIdentifier, orgIdentifier, accountIdentifier);
      return false;
    }
    return true;
  }
}
