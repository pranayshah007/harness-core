/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;

import io.harness.freeze.beans.FreezeErrorResponseDTO;
import io.harness.freeze.beans.FreezeResponse;
import io.harness.freeze.beans.FreezeResponseDTO;
import io.harness.freeze.beans.FreezeResponseWrapperDTO;
import io.harness.freeze.beans.FreezeStatus;
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

  static final String FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE = "Freeze Config for id: %s doesn't exist";
  static final String FREEZE_CONFIG_ALREADY_EXISTS_ERROR_TEMPLATE =
      "Freeze Config for freezeIdentifier: %s , ogrIdentifier: %s , projIdentifier: %s already exist";

  @Inject
  public FreezeCRUDServiceImpl(FreezeConfigRepository freezeConfigRepository) {
    this.freezeConfigRepository = freezeConfigRepository;
  }

  @Override
  public FreezeResponse createFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId) {
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntity(accountId, orgId, projectId, deploymentFreezeYaml);
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(accountId,
            freezeConfigEntity.getOrgIdentifier(), freezeConfigEntity.getProjectIdentifier(),
            freezeConfigEntity.getIdentifier());
    if (freezeConfigEntityOptional.isPresent()) {
      return FreezeErrorResponseDTO.builder()
          .id(freezeConfigEntity.getIdentifier())
          .errorMessage(String.format(FREEZE_CONFIG_ALREADY_EXISTS_ERROR_TEMPLATE, freezeConfigEntity.getIdentifier(),
              freezeConfigEntity.getOrgIdentifier(), freezeConfigEntity.getProjectIdentifier()))
          .build();
    } else {
      freezeConfigRepository.save(freezeConfigEntity);
    }
    return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
  }

  @Override
  public FreezeResponse updateFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId, String freezeIdentifier) {
    FreezeConfigEntity updatedFreezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntity(accountId, orgId, projectId, deploymentFreezeYaml);
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      freezeConfigRepository.update(
          FreezeFilterHelper.getFreezeEqualityCriteria(updatedFreezeConfigEntity), updatedFreezeConfigEntity);
      return NGFreezeDtoMapper.prepareFreezeResponseDto(updatedFreezeConfigEntity);
    } else {
      return FreezeErrorResponseDTO.builder()
          .id(freezeIdentifier)
          .errorMessage(String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier))
          .build();
    }
  }

  @Override
  public FreezeResponse deleteFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntityToDelete = freezeConfigEntityOptional.get();
      freezeConfigRepository.delete(FreezeFilterHelper.getFreezeEqualityCriteria(freezeConfigEntityToDelete));
      return null;
    } else {
      return FreezeErrorResponseDTO.builder()
          .id(freezeIdentifier)
          .errorMessage(String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier))
          .build();
    }
  }

  @Override
  public FreezeResponse getFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
    } else {
      return FreezeErrorResponseDTO.builder()
          .id(freezeIdentifier)
          .errorMessage(String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier))
          .build();
    }
  }

  @Override
  public Page<FreezeResponse> list(Criteria criteria, Pageable pageRequest) {
    return freezeConfigRepository.findAll(criteria, pageRequest).map(NGFreezeDtoMapper::prepareFreezeResponseDto);
  }

  @Override
  public FreezeResponse deleteFreezeConfigs(
      String freezeIdentifiersString, String accountId, String orgId, String projectId) {
    String[] freezeIdentifiers = freezeIdentifiersString.split(",");
    int successful = 0;
    int failed = 0;
    List<FreezeErrorResponseDTO> freezeErrorResponseDTOList = new LinkedList<>();
    for (String freezeIdentifier : freezeIdentifiers) {
      FreezeResponse freezeResponse = deleteFreezeConfig(freezeIdentifier, accountId, orgId, projectId);
      if (freezeResponse instanceof FreezeErrorResponseDTO) {
        freezeErrorResponseDTOList.add((FreezeErrorResponseDTO) freezeResponse);
        failed++;
      } else {
        successful++;
      }
    }

    return FreezeResponseWrapperDTO.builder()
        .success(successful)
        .failed(failed)
        .freezeErrorResponseDTOList(freezeErrorResponseDTOList)
        .build();
  }

  @Override
  public FreezeResponse updateActiveStatus(
      FreezeStatus freezeStatus, String accountId, String orgId, String projectId, List<String> freezeIdentifiers) {
    int successful = 0;
    int failed = 0;
    List<FreezeErrorResponseDTO> freezeErrorResponseDTOList = new LinkedList<>();
    List<FreezeResponseDTO> freezeResponseDTOs = new LinkedList<>();
    for (String freezeIdentifier : freezeIdentifiers) {
      FreezeResponse freezeResponse = updateActiveStatus(freezeIdentifier, accountId, orgId, projectId, freezeStatus);
      if (freezeResponse instanceof FreezeErrorResponseDTO) {
        freezeErrorResponseDTOList.add((FreezeErrorResponseDTO) freezeResponse);
        failed++;
      } else if (freezeResponse instanceof FreezeResponseDTO) {
        freezeResponseDTOs.add((FreezeResponseDTO) freezeResponse);
        successful++;
      }
    }

    return FreezeResponseWrapperDTO.builder()
        .success(successful)
        .failed(failed)
        .freezeErrorResponseDTOList(freezeErrorResponseDTOList)
        .successfulFreezeResponseDTOList(freezeResponseDTOs)
        .build();
  }

  private FreezeResponse updateActiveStatus(
      String freezeIdentifier, String accountId, String orgId, String projectId, FreezeStatus freezeStatus) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      String deploymentFreezeYaml = freezeConfigEntity.getYaml();
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(deploymentFreezeYaml);
      freezeConfig.getFreezeInfoConfig().setActive(freezeStatus);
      freezeConfigEntity.setYaml(NGFreezeDtoMapper.toYaml(freezeConfig));
      freezeConfigEntity.setStatus(freezeStatus);
      freezeConfigRepository.update(
          FreezeFilterHelper.getFreezeEqualityCriteria(freezeConfigEntity), freezeConfigEntity);
      return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
    } else {
      return FreezeErrorResponseDTO.builder()
          .id(freezeIdentifier)
          .errorMessage(String.format(FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE, freezeIdentifier))
          .build();
    }
  }
}
