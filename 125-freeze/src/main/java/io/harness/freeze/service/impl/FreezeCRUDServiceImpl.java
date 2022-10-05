/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import io.harness.exception.DuplicateEntityException;
import io.harness.exception.EntityNotFoundException;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.response.FreezeErrorResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseWrapperDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.helpers.FreezeFilterHelper;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.service.FreezeCRUDService;
import io.harness.repositories.FreezeConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class FreezeCRUDServiceImpl implements FreezeCRUDService {
  private final FreezeConfigRepository freezeConfigRepository;

  static final String FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE =
      "Freeze Config for freezeIdentifier: %s , ogrIdentifier: %s , projIdentifier: %s doesn't exist";
  static final String FREEZE_CONFIG_ALREADY_EXISTS_ERROR_TEMPLATE =
      "Freeze Config for freezeIdentifier: %s , ogrIdentifier: %s , projIdentifier: %s already exist";

  @Inject
  public FreezeCRUDServiceImpl(FreezeConfigRepository freezeConfigRepository) {
    this.freezeConfigRepository = freezeConfigRepository;
  }

  @Override
  public FreezeResponseDTO createFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId) {
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityManual(accountId, orgId, projectId, deploymentFreezeYaml);
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(accountId,
            freezeConfigEntity.getOrgIdentifier(), freezeConfigEntity.getProjectIdentifier(),
            freezeConfigEntity.getIdentifier());
    if (freezeConfigEntityOptional.isPresent()) {
      throw new DuplicateEntityException(
          String.format(FREEZE_CONFIG_ALREADY_EXISTS_ERROR_TEMPLATE, freezeConfigEntity.getIdentifier(),
              freezeConfigEntity.getOrgIdentifier(), freezeConfigEntity.getProjectIdentifier()));
    } else {
      freezeConfigRepository.save(freezeConfigEntity);
    }
    return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
  }

  @Override
  public FreezeResponseDTO manageGlobalFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId) {
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityGlobal(accountId, orgId, projectId, deploymentFreezeYaml);
    freezeConfigRepository.upsert(FreezeFilterHelper.getFreezeEqualityCriteria(freezeConfigEntity), freezeConfigEntity);
    return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
  }

  @Override
  public Optional<Boolean> isGlobalDeploymentFreezeActive(String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> entity;
    Boolean result = false;
    if (accountId != null) {
      entity = freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(accountId, null, null);
      if (entity.isPresent() && entity.get().getStatus() != null
          && entity.get().getStatus().equals(FreezeStatus.ENABLED)) {
        result = true;
      }
    }
    if (!result && orgId != null) {
      entity = freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(accountId, orgId, null);
      if (entity.isPresent() && entity.get().getStatus() != null
          && entity.get().getStatus().equals(FreezeStatus.ENABLED)) {
        result = true;
      }
    }
    if (!result && projectId != null) {
      entity =
          freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(accountId, orgId, projectId);
      if (entity.isPresent() && entity.get().getStatus() != null
          && entity.get().getStatus().equals(FreezeStatus.ENABLED)) {
        result = true;
      }
    }
    return Optional.ofNullable(result);
  }

  @Override
  public FreezeResponseDTO updateFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId, String freezeIdentifier) {
    FreezeConfigEntity updatedFreezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityManual(accountId, orgId, projectId, deploymentFreezeYaml);
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      freezeConfigRepository.update(
          FreezeFilterHelper.getFreezeEqualityCriteria(updatedFreezeConfigEntity), updatedFreezeConfigEntity);
      return NGFreezeDtoMapper.prepareFreezeResponseDto(updatedFreezeConfigEntity);
    } else {
      throw new EntityNotFoundException(
          String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier, orgId, projectId));
    }
  }

  @Override
  public void deleteFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntityToDelete = freezeConfigEntityOptional.get();
      freezeConfigRepository.delete(FreezeFilterHelper.getFreezeEqualityCriteria(freezeConfigEntityToDelete));
      return;
    } else {
      throw new EntityNotFoundException(
          String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier, orgId, projectId));
    }
  }

  @Override
  public FreezeResponseDTO getFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
    } else {
      throw new EntityNotFoundException(
          String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier, orgId, projectId));
    }
  }

  @Override
  public Page<FreezeSummaryResponseDTO> list(Criteria criteria, Pageable pageRequest) {
    return freezeConfigRepository.findAll(criteria, pageRequest)
        .map(NGFreezeDtoMapper::prepareFreezeResponseSummaryDto);
  }

  @Override
  public FreezeResponseWrapperDTO deleteFreezeConfigs(
      List<String> freezeIdentifiers, String accountId, String orgId, String projectId) {
    int successful = 0;
    int failed = 0;
    List<FreezeErrorResponseDTO> freezeErrorResponseDTOList = new LinkedList<>();
    for (String freezeIdentifier : freezeIdentifiers) {
      try {
        deleteFreezeConfig(freezeIdentifier, accountId, orgId, projectId);
        successful++;
      } catch (EntityNotFoundException e) {
        FreezeErrorResponseDTO freezeResponse =
            FreezeErrorResponseDTO.builder().id(freezeIdentifier).errorMessage(e.getMessage()).build();
        freezeErrorResponseDTOList.add(freezeResponse);
        failed++;
      }
    }
    return FreezeResponseWrapperDTO.builder()
        .noOfSuccess(successful)
        .noOfFailed(failed)
        .freezeErrorResponseDTOList(freezeErrorResponseDTOList)
        .build();
  }

  @Override
  public FreezeResponseWrapperDTO updateActiveStatus(
      FreezeStatus freezeStatus, String accountId, String orgId, String projectId, List<String> freezeIdentifiers) {
    int successful = 0;
    int failed = 0;
    List<FreezeErrorResponseDTO> freezeErrorResponseDTOList = new LinkedList<>();
    List<FreezeResponseDTO> freezeResponseDTOs = new LinkedList<>();
    for (String freezeIdentifier : freezeIdentifiers) {
      try {
        FreezeResponseDTO freezeResponse =
            updateActiveStatus(freezeIdentifier, accountId, orgId, projectId, freezeStatus);
        freezeResponseDTOs.add(freezeResponse);
        successful++;
      } catch (EntityNotFoundException e) {
        FreezeErrorResponseDTO errorResponseDTO =
            FreezeErrorResponseDTO.builder().id(freezeIdentifier).errorMessage(e.getMessage()).build();
        freezeErrorResponseDTOList.add(errorResponseDTO);
        failed++;
      }
    }

    return FreezeResponseWrapperDTO.builder()
        .noOfSuccess(successful)
        .noOfFailed(failed)
        .freezeErrorResponseDTOList(freezeErrorResponseDTOList)
        .successfulFreezeResponseDTOList(freezeResponseDTOs)
        .build();
  }

  private FreezeResponseDTO updateActiveStatus(
      String freezeIdentifier, String accountId, String orgId, String projectId, FreezeStatus freezeStatus) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      String deploymentFreezeYaml = freezeConfigEntity.getYaml();
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(deploymentFreezeYaml);
      freezeConfig.getFreezeInfoConfig().setStatus(freezeStatus);
      freezeConfigEntity.setYaml(NGFreezeDtoMapper.toYaml(freezeConfig));
      freezeConfigEntity.setStatus(freezeStatus);
      freezeConfigRepository.update(
          FreezeFilterHelper.getFreezeEqualityCriteria(freezeConfigEntity), freezeConfigEntity);
      return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
    } else {
      throw new EntityNotFoundException(
          String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier, orgId, projectId));
    }
  }
}
