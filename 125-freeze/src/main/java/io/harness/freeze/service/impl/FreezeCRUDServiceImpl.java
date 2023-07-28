/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service.impl;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.events.FreezeEntityCreateEvent;
import io.harness.events.FreezeEntityDeleteEvent;
import io.harness.events.FreezeEntityUpdateEvent;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.response.FreezeErrorResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseWrapperDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.response.FrozenExecutionDetails;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.freeze.helpers.FreezeFilterHelper;
import io.harness.freeze.helpers.FreezeServiceHelper;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeCRUDService;
import io.harness.freeze.service.FreezeSchemaService;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.FreezeConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Singleton
@Slf4j
public class FreezeCRUDServiceImpl implements FreezeCRUDService {
  private final FreezeConfigRepository freezeConfigRepository;
  private final FreezeSchemaService freezeSchemaService;
  private final FrozenExecutionService frozenExecutionService;
  private final NotificationHelper notificationHelper;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  static final String FREEZE_CONFIG_DOES_NOT_EXIST_ERROR_TEMPLATE =
      "Freeze Config for freezeIdentifier: %s , ogrIdentifier: %s , projIdentifier: %s doesn't exist";
  static final String FREEZE_CONFIG_ALREADY_EXISTS_ERROR_TEMPLATE =
      "Freeze Config for freezeIdentifier: %s , ogrIdentifier: %s , projIdentifier: %s already exist";

  static final String GLOBAL_FREEZE_CONFIG_WITH_WRONG_IDENTIFIER =
      "Can't update global freeze with identifier: '%s'. Please use _GLOBAL_ as the id";

  @Inject
  public FreezeCRUDServiceImpl(FreezeConfigRepository freezeConfigRepository, FreezeSchemaService freezeSchemaService,
      FrozenExecutionService frozenExecutionService, NotificationHelper notificationHelper,
      TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.freezeConfigRepository = freezeConfigRepository;
    this.freezeSchemaService = freezeSchemaService;
    this.frozenExecutionService = frozenExecutionService;
    this.notificationHelper = notificationHelper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public FreezeResponseDTO createFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId) {
    try {
      freezeSchemaService.validateYamlSchema(deploymentFreezeYaml);
    } catch (IOException e) {
      log.error("Freeze Config could not be validated for yaml: " + deploymentFreezeYaml);
    }
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityManual(accountId, orgId, projectId, deploymentFreezeYaml);
    return createFreezeConfig(freezeConfigEntity);
  }

  public FreezeResponseDTO createGlobalFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId) {
    try {
      freezeSchemaService.validateYamlSchema(deploymentFreezeYaml);
    } catch (IOException e) {
      log.error("Freeze Config could not be validated for yaml: " + deploymentFreezeYaml);
    }
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityGlobal(accountId, orgId, projectId, deploymentFreezeYaml);
    return createFreezeConfig(freezeConfigEntity);
  }

  private FreezeResponseDTO createFreezeConfig(FreezeConfigEntity freezeConfigEntity) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            freezeConfigEntity.getAccountId(), freezeConfigEntity.getOrgIdentifier(),
            freezeConfigEntity.getProjectIdentifier(), freezeConfigEntity.getIdentifier());
    if (freezeConfigEntityOptional.isPresent()) {
      throw new DuplicateEntityException(createFreezeConfigAlreadyExistsMessage(freezeConfigEntity.getOrgIdentifier(),
          freezeConfigEntity.getProjectIdentifier(), freezeConfigEntity.getIdentifier()));
    } else {
      updateNextIterations(freezeConfigEntity);
      updateShouldSendNotification(freezeConfigEntity);
      Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        FreezeConfigEntity freezeConfig = freezeConfigRepository.save(freezeConfigEntity);
        outboxService.save(new FreezeEntityCreateEvent(freezeConfig.getAccountId(), freezeConfig));
        return freezeConfig;
      }));
    }
    return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
  }

  private void updateShouldSendNotification(FreezeConfigEntity freezeConfigEntity) {
    freezeConfigEntity.setShouldSendNotification(true);
  }

  @Override
  public FreezeResponseDTO manageGlobalFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId) {
    try {
      freezeSchemaService.validateYamlSchema(deploymentFreezeYaml);
    } catch (IOException e) {
      log.error("Freeze Config could not be validated for yaml: " + deploymentFreezeYaml);
    }
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityGlobal(accountId, orgId, projectId, deploymentFreezeYaml);
    if (!freezeConfigEntity.getIdentifier().equals("_GLOBAL_")) {
      throw new NGFreezeException(
          String.format(GLOBAL_FREEZE_CONFIG_WITH_WRONG_IDENTIFIER, freezeConfigEntity.getIdentifier()));
    }
    FreezeConfigEntity oldFreezeConfigEntity = null;
    Optional<FreezeConfigEntity> oldFreezeConfigEntityOptional =
        getOldFreezeConfig(accountId, orgId, projectId, freezeConfigEntity.getIdentifier());
    if (oldFreezeConfigEntityOptional.isPresent()) {
      oldFreezeConfigEntity = oldFreezeConfigEntityOptional.get();
    }
    FreezeConfigEntity finalOldFreezeConfigEntity = FreezeConfigEntity.builder()
                                                        .accountId(oldFreezeConfigEntity.getAccountId())
                                                        .freezeScope(oldFreezeConfigEntity.getFreezeScope())
                                                        .orgIdentifier(oldFreezeConfigEntity.getOrgIdentifier())
                                                        .projectIdentifier(oldFreezeConfigEntity.getProjectIdentifier())
                                                        .identifier(oldFreezeConfigEntity.getIdentifier())
                                                        .uuid(oldFreezeConfigEntity.getUuid())
                                                        .yaml(oldFreezeConfigEntity.getYaml())
                                                        .type(oldFreezeConfigEntity.getType())
                                                        .name(oldFreezeConfigEntity.getName())
                                                        .status(oldFreezeConfigEntity.getStatus())
                                                        .build();
    FreezeConfigEntity updatedFreezeConfigEntity = updateFreezeConfig(
        freezeConfigEntity, orgId, projectId, freezeConfigEntity.getIdentifier(), oldFreezeConfigEntityOptional);
    deleteFreezeWindowsIfDisabled(updatedFreezeConfigEntity);
    updateNextIterations(updatedFreezeConfigEntity);
    FreezeConfigEntity finalUpdatedFreezeConfigEntity = updatedFreezeConfigEntity;
    updatedFreezeConfigEntity = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      FreezeConfigEntity newFreezeConfigEntity = freezeConfigRepository.save(finalUpdatedFreezeConfigEntity);
      outboxService.save(new FreezeEntityUpdateEvent(accountId, newFreezeConfigEntity, finalOldFreezeConfigEntity));
      return newFreezeConfigEntity;
    }));
    return NGFreezeDtoMapper.prepareFreezeResponseDto(updatedFreezeConfigEntity);
  }

  @Override
  public FreezeConfigEntity updateExistingFreezeConfigEntity(FreezeConfigEntity freezeConfigEntity) {
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      FreezeConfigEntity newFreezeConfigEntity = freezeConfigRepository.save(freezeConfigEntity);
      return newFreezeConfigEntity;
    }));
  }
  public void updateNextIterations(FreezeConfigEntity freezeConfigEntity) {
    if (freezeConfigEntity.getStatus().equals(FreezeStatus.ENABLED)) {
      freezeConfigEntity.setNextIteration(recalculateNextIterations(freezeConfigEntity));
    }
  }

  private Long recalculateNextIterations(FreezeConfigEntity freezeConfigEntity) {
    if (freezeConfigEntity.getStatus().equals(FreezeStatus.ENABLED)) {
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(freezeConfigEntity.getYaml());
      FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();
      List<FreezeWindow> windows = freezeInfoConfig.getWindows();
      List<Long> nextIterations = FreezeTimeUtils.fetchUpcomingTimeWindow(windows);
      if (!isEmpty(nextIterations)) {
        return nextIterations.get(0);
      }
    }
    return null;
  }

  @Override
  public FreezeResponseDTO updateFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId, String freezeIdentifier) {
    try {
      freezeSchemaService.validateYamlSchema(deploymentFreezeYaml);
    } catch (IOException e) {
      log.error("Freeze Config could not be validated for yaml: " + deploymentFreezeYaml);
    }
    FreezeConfigEntity updatedFreezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntityManual(accountId, orgId, projectId, deploymentFreezeYaml);
    FreezeConfigEntity oldFreezeConfigEntity = null;
    Optional<FreezeConfigEntity> oldFreezeConfigEntityOptional =
        getOldFreezeConfig(accountId, orgId, projectId, updatedFreezeConfigEntity.getIdentifier());
    if (oldFreezeConfigEntityOptional.isPresent()) {
      oldFreezeConfigEntity = oldFreezeConfigEntityOptional.get();
    }
    FreezeConfigEntity finalOldFreezeConfigEntity = FreezeConfigEntity.builder()
                                                        .accountId(oldFreezeConfigEntity.getAccountId())
                                                        .freezeScope(oldFreezeConfigEntity.getFreezeScope())
                                                        .orgIdentifier(oldFreezeConfigEntity.getOrgIdentifier())
                                                        .projectIdentifier(oldFreezeConfigEntity.getProjectIdentifier())
                                                        .identifier(oldFreezeConfigEntity.getIdentifier())
                                                        .uuid(oldFreezeConfigEntity.getUuid())
                                                        .yaml(oldFreezeConfigEntity.getYaml())
                                                        .type(oldFreezeConfigEntity.getType())
                                                        .name(oldFreezeConfigEntity.getName())
                                                        .status(oldFreezeConfigEntity.getStatus())
                                                        .build();
    updatedFreezeConfigEntity = updateFreezeConfig(updatedFreezeConfigEntity, orgId, projectId,
        updatedFreezeConfigEntity.getIdentifier(), oldFreezeConfigEntityOptional);
    updateNextIterations(updatedFreezeConfigEntity);
    FreezeConfigEntity finalUpdatedFreezeConfigEntity = updatedFreezeConfigEntity;
    updatedFreezeConfigEntity = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      FreezeConfigEntity newFreezeConfigEntity = freezeConfigRepository.save(finalUpdatedFreezeConfigEntity);
      outboxService.save(new FreezeEntityUpdateEvent(accountId, newFreezeConfigEntity, finalOldFreezeConfigEntity));
      return newFreezeConfigEntity;
    }));

    return NGFreezeDtoMapper.prepareFreezeResponseDto(updatedFreezeConfigEntity);
  }

  @Override
  public void deleteFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntityToDelete = freezeConfigEntityOptional.get();
      boolean success = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        boolean deleted =
            freezeConfigRepository.delete(FreezeFilterHelper.getFreezeEqualityCriteria(freezeConfigEntityToDelete));
        outboxService.save(new FreezeEntityDeleteEvent(accountId, freezeConfigEntityToDelete));
        return deleted;
      }));
      if (success) {
        log.info("Freeze window successfully deleted");
      } else {
        log.info("Freeze window deletion was not successful");
      }
      return;
    } else {
      throw new EntityNotFoundException(createFreezeConfigDoesntExistMessage(orgId, projectId, freezeIdentifier));
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
      throw new EntityNotFoundException(createFreezeConfigDoesntExistMessage(orgId, projectId, freezeIdentifier));
    }
  }

  @Override
  public FrozenExecutionDetails getFrozenExecutionDetails(
      String accountId, String orgId, String projectId, String planExecutionId, String baseUrl) {
    Optional<FrozenExecution> frozenExecutionOptional =
        frozenExecutionService.getFrozenExecution(accountId, orgId, projectId, planExecutionId);
    if (frozenExecutionOptional.isEmpty()) {
      return FrozenExecutionDetails.builder().freezeList(new ArrayList<>()).build();
    }
    FrozenExecution frozenExecution = frozenExecutionOptional.get();
    List<FreezeSummaryResponseDTO> manualFreezeList = frozenExecution.getManualFreezeList();
    List<FreezeSummaryResponseDTO> globalFreezeList = frozenExecution.getGlobalFreezeList();
    Map<Scope, List<String>> manualFreezeIdentifiersForEachScope = FreezeServiceHelper.getMapForEachScope();
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : manualFreezeList) {
      manualFreezeIdentifiersForEachScope.get(freezeSummaryResponseDTO.getFreezeScope())
          .add(freezeSummaryResponseDTO.getIdentifier());
    }
    List<FreezeSummaryResponseDTO> freezeList =
        NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(getFreezeConfigEntityByIdentifierListAndScope(
            accountId, orgId, projectId, manualFreezeIdentifiersForEachScope));
    freezeList.addAll(getGlobalFreezeSummaryResponseDTO(accountId, orgId, projectId, globalFreezeList));

    return FrozenExecutionDetails.builder()
        .freezeList(getFrozenExecutionDetailList(accountId, freezeList, baseUrl))
        .build();
  }

  private List<FrozenExecutionDetails.FrozenExecutionDetail> getFrozenExecutionDetailList(
      String accountId, List<FreezeSummaryResponseDTO> freezeList, String baseUrl) {
    List<FrozenExecutionDetails.FrozenExecutionDetail> frozenExecutionDetailList = new ArrayList<>();
    for (FreezeSummaryResponseDTO freeze : freezeList) {
      String url = "";
      final String orgId = freeze.getOrgIdentifier();
      final String projectId = freeze.getProjectIdentifier();
      switch (freeze.getType()) {
        case GLOBAL:
          url = notificationHelper.getGlobalFreezeUrl(baseUrl, accountId, orgId, projectId);
          break;
        case MANUAL:
          url = notificationHelper.getManualFreezeUrl(baseUrl, accountId, orgId, projectId, freeze.getIdentifier());
          break;
        default:
          break;
      }
      frozenExecutionDetailList.add(
          FrozenExecutionDetails.FrozenExecutionDetail.builder().freeze(freeze).url(url).build());
    }
    return frozenExecutionDetailList;
  }

  private List<FreezeConfigEntity> getFreezeConfigEntityByIdentifierListAndScope(
      String accountId, String orgId, String projectId, Map<Scope, List<String>> manualFreezeIdentifiersForEachScope) {
    List<FreezeConfigEntity> freezeConfigEntityList = new ArrayList<>();
    for (Map.Entry<Scope, List<String>> entry : manualFreezeIdentifiersForEachScope.entrySet()) {
      Scope scope = entry.getKey();
      switch (scope) {
        case ACCOUNT:
          freezeConfigEntityList.addAll(
              freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
                  accountId, null, null, entry.getValue()));
          break;
        case ORG:
          freezeConfigEntityList.addAll(
              freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
                  accountId, orgId, null, entry.getValue()));
          break;
        case PROJECT:
          freezeConfigEntityList.addAll(
              freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
                  accountId, orgId, projectId, entry.getValue()));
          break;
        default:
          break;
      }
    }
    return freezeConfigEntityList;
  }

  private List<FreezeSummaryResponseDTO> getGlobalFreezeSummaryResponseDTO(
      String accountId, String orgId, String projectId, List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList) {
    List<FreezeSummaryResponseDTO> freezeConfigEntityList = new ArrayList<>();
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : freezeSummaryResponseDTOList) {
      Scope scope = freezeSummaryResponseDTO.getFreezeScope();
      switch (scope) {
        case ACCOUNT:
          freezeConfigEntityList.add(getGlobalFreezeSummary(accountId, null, null));
          break;
        case ORG:
          freezeConfigEntityList.add(getGlobalFreezeSummary(accountId, orgId, null));
          break;
        case PROJECT:
          freezeConfigEntityList.add(getGlobalFreezeSummary(accountId, orgId, projectId));
          break;
        default:
          break;
      }
    }
    return freezeConfigEntityList;
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
  public void deleteByScope(io.harness.beans.Scope scope) {
    Criteria criteria =
        createScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    freezeConfigRepository.delete(criteria);
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(FreezeConfigEntityKeys.accountId).is(accountIdentifier);
    criteria.and(FreezeConfigEntityKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(FreezeConfigEntityKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
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

  @Override
  public FreezeResponseDTO getGlobalFreeze(String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(
            accountId, orgId, projectId, null);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      String freezeYaml = freezeConfigEntity.getYaml();
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(freezeYaml);
      boolean update = NGFreezeDtoMapper.setGlobalFreezeStatus(freezeConfig);
      if (update) {
        freezeConfigEntity = updateGlobalFreezeStatus(freezeConfig, freezeConfigEntity);
      }
      return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
    } else {
      String globalFreezeYaml = createGlobalFreezeConfigYaml(orgId, projectId);
      try {
        freezeSchemaService.validateYamlSchema(globalFreezeYaml);
      } catch (IOException e) {
        log.error("Freeze Config could not be validated for yaml: " + globalFreezeYaml);
      }
      return createGlobalFreezeConfig(globalFreezeYaml, accountId, orgId, projectId);
    }
  }

  @Override
  public FreezeSummaryResponseDTO getGlobalFreezeSummary(String accountId, String orgId, String projectId) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(
            accountId, orgId, projectId, null);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(freezeConfigEntity);
    } else {
      String globalFreezeYaml = createGlobalFreezeConfigYaml(orgId, projectId);
      try {
        freezeSchemaService.validateYamlSchema(globalFreezeYaml);
      } catch (IOException e) {
        log.error("Freeze Config could not be validated for yaml: " + globalFreezeYaml);
      }
      return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(
          createGlobalFreezeConfig(globalFreezeYaml, accountId, orgId, projectId));
    }
  }

  @Override
  public List<FreezeResponseDTO> getParentGlobalFreezeSummary(String accountId, String orgId, String projectId) {
    List<FreezeResponseDTO> parentGlobalFreeze = new LinkedList<>();
    Scope scope = NGFreezeDtoMapper.getScopeFromFreezeDto(orgId, projectId);
    switch (scope) {
      case PROJECT:
        parentGlobalFreeze.add(getGlobalFreeze(accountId, orgId, null));
        // fallthrough to ignore
      case ORG:
        parentGlobalFreeze.add(getGlobalFreeze(accountId, null, null));
        // fallthrough to ignore
      case ACCOUNT:
      default:
        break;
    }
    return parentGlobalFreeze;
  }

  private FreezeResponseDTO updateActiveStatus(
      String freezeIdentifier, String accountId, String orgId, String projectId, FreezeStatus freezeStatus) {
    Optional<FreezeConfigEntity> freezeConfigEntityOptional =
        freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgId, projectId, freezeIdentifier);
    if (freezeConfigEntityOptional.isPresent()) {
      FreezeConfigEntity freezeConfigEntity = freezeConfigEntityOptional.get();
      FreezeConfigEntity finalOldFreezeConfigEntity = FreezeConfigEntity.builder()
                                                          .accountId(freezeConfigEntity.getAccountId())
                                                          .freezeScope(freezeConfigEntity.getFreezeScope())
                                                          .orgIdentifier(freezeConfigEntity.getOrgIdentifier())
                                                          .projectIdentifier(freezeConfigEntity.getProjectIdentifier())
                                                          .identifier(freezeConfigEntity.getIdentifier())
                                                          .uuid(freezeConfigEntity.getUuid())
                                                          .yaml(freezeConfigEntity.getYaml())
                                                          .type(freezeConfigEntity.getType())
                                                          .name(freezeConfigEntity.getName())
                                                          .status(freezeConfigEntity.getStatus())
                                                          .build();
      String deploymentFreezeYaml = freezeConfigEntity.getYaml();
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(deploymentFreezeYaml);
      freezeConfig.getFreezeInfoConfig().setStatus(freezeStatus);
      String yaml = NGFreezeDtoMapper.toYaml(freezeConfig);
      NGFreezeDtoMapper.validateFreezeYaml(freezeConfig, freezeConfigEntity.getOrgIdentifier(),
          freezeConfigEntity.getProjectIdentifier(), freezeConfigEntity.getType(), freezeConfigEntity.getFreezeScope());
      freezeConfigEntity.setYaml(yaml);
      freezeConfigEntity.setStatus(freezeStatus);

      if (FreezeStatus.ENABLED.equals(freezeStatus)) {
        freezeConfigEntity.setShouldSendNotification(true);
      } else {
        freezeConfigEntity.setShouldSendNotification(false);
      }
      updateNextIterations(freezeConfigEntity);
      FreezeConfigEntity finalFreezeConfigEntity = freezeConfigEntity;
      freezeConfigEntity = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        FreezeConfigEntity newFreezeConfigEntity = freezeConfigRepository.save(finalFreezeConfigEntity);
        outboxService.save(new FreezeEntityUpdateEvent(accountId, newFreezeConfigEntity, finalOldFreezeConfigEntity));
        return newFreezeConfigEntity;
      }));
      return NGFreezeDtoMapper.prepareFreezeResponseDto(freezeConfigEntity);
    } else {
      throw new EntityNotFoundException(createFreezeConfigDoesntExistMessage(orgId, projectId, freezeIdentifier));
    }
  }

  private String createGlobalFreezeConfigYaml(String orgId, String projectId) {
    FreezeConfig freezeConfig =
        FreezeConfig.builder()
            .freezeInfoConfig(FreezeInfoConfig.builder()
                                  .identifier("_GLOBAL_")
                                  .name("Global Freeze")
                                  .projectIdentifier(projectId)
                                  .orgIdentifier(orgId)
                                  .description(ParameterField.<String>builder().value("Global Freeze").build())
                                  .status(FreezeStatus.DISABLED)
                                  .build())
            .build();
    return NGFreezeDtoMapper.toYaml(freezeConfig);
  }

  private FreezeConfigEntity updateGlobalFreezeStatus(
      FreezeConfig freezeConfig, FreezeConfigEntity freezeConfigEntity) {
    freezeConfig.getFreezeInfoConfig().setStatus(FreezeStatus.DISABLED);
    freezeConfigEntity.setYaml(NGFreezeDtoMapper.toYaml(freezeConfig));
    freezeConfigEntity.setStatus(FreezeStatus.DISABLED);
    deleteFreezeWindowsIfDisabled(freezeConfigEntity);
    return freezeConfigRepository.save(freezeConfigEntity);
  }

  private FreezeConfigEntity updateFreezeConfig(FreezeConfigEntity updatedFreezeConfigEntity, String orgId,
      String projectId, String freezeIdentifier, Optional<FreezeConfigEntity> oldFreezeConfigEntityOptional) {
    if (oldFreezeConfigEntityOptional.isPresent()) {
      updatedFreezeConfigEntity =
          NGFreezeDtoMapper.updateOldFreezeConfig(updatedFreezeConfigEntity, oldFreezeConfigEntityOptional.get());
      return updatedFreezeConfigEntity;
    } else {
      throw new EntityNotFoundException(createFreezeConfigDoesntExistMessage(orgId, projectId, freezeIdentifier));
    }
  }

  private Optional<FreezeConfigEntity> getOldFreezeConfig(
      String accountId, String orgId, String projectId, String freezeIdentifier) {
    return freezeConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountId, orgId, projectId, freezeIdentifier);
  }

  private void deleteFreezeWindowsIfDisabled(FreezeConfigEntity freezeConfigEntity) {
    if (FreezeStatus.DISABLED.equals(freezeConfigEntity.getStatus())) {
      String deploymentFreezeYaml = freezeConfigEntity.getYaml();
      FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(deploymentFreezeYaml);
      freezeConfig.getFreezeInfoConfig().setWindows(null);
      freezeConfigEntity.setYaml(NGFreezeDtoMapper.toYaml(freezeConfig));
    }
  }

  private String createFreezeConfigAlreadyExistsMessage(String orgId, String projId, String freezeIdentifier) {
    StringBuilder errorBuilder = new StringBuilder("Freeze Config for freezeIdentifier: ");
    errorBuilder.append(freezeIdentifier);
    if (EmptyPredicate.isNotEmpty(orgId)) {
      errorBuilder.append(" ,orgIdentifier: ");
      errorBuilder.append(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projId)) {
      errorBuilder.append(" ,projIdentifier: ");
      errorBuilder.append(projId);
    }
    errorBuilder.append(" already exist");
    return errorBuilder.toString();
  }

  private String createFreezeConfigDoesntExistMessage(String orgId, String projId, String freezeIdentifier) {
    StringBuilder errorBuilder = new StringBuilder("Freeze Config for freezeIdentifier: ");
    errorBuilder.append(freezeIdentifier);
    appendOrgIdentifierToMessage(errorBuilder, orgId);
    appendProjectIdentifierToMessage(errorBuilder, projId);
    errorBuilder.append(" doesn't exist");
    return errorBuilder.toString();
  }

  private void appendOrgIdentifierToMessage(StringBuilder stringBuilder, String orgId) {
    if (EmptyPredicate.isNotEmpty(orgId)) {
      stringBuilder.append(" ,orgIdentifier: ");
      stringBuilder.append(orgId);
    }
  }

  private void appendProjectIdentifierToMessage(StringBuilder stringBuilder, String projId) {
    if (EmptyPredicate.isNotEmpty(projId)) {
      stringBuilder.append(" ,projIdentifier: ");
      stringBuilder.append(projId);
    }
  }
}
