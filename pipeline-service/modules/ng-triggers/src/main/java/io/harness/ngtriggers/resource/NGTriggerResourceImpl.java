/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.NGTriggerCatalogDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.exceptions.InvalidTriggerYamlException;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.utils.CryptoUtils;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerResourceImpl implements NGTriggerResource {
  private final NGTriggerService ngTriggerService;

  private final NGTriggerEventsService ngTriggerEventsService;

  private final NGTriggerEventHistoryResource ngTriggerEventHistoryResource;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final AccessControlClient accessControlClient;

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<NGTriggerResponseDTO> create(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, @NotNull String yaml, boolean ignoreError,
      boolean withServiceV2) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    NGTriggerEntity createdEntity = null;
    try {
      TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
          accountIdentifier, orgIdentifier, projectIdentifier, yaml, withServiceV2);
      ngTriggerService.validateTriggerConfig(triggerDetails);

      if (ignoreError) {
        createdEntity = ngTriggerService.create(triggerDetails.getNgTriggerEntity());
      } else {
        ngTriggerService.validatePipelineRef(triggerDetails);
        createdEntity = ngTriggerService.create(triggerDetails.getNgTriggerEntity());
      }
      return ResponseDTO.newResponse(
          createdEntity.getVersion().toString(), ngTriggerElementMapper.toResponseDTO(createdEntity));
    } catch (InvalidTriggerYamlException e) {
      return ResponseDTO.newResponse(ngTriggerElementMapper.toErrorDTO(e));
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while Saving Trigger: " + e.getMessage());
    }
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<NGTriggerResponseDTO> get(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);

    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    return ResponseDTO.newResponse(ngTriggerEntity.get().getVersion().toString(),
        ngTriggerEntity.map(ngTriggerElementMapper::toResponseDTO).orElse(null));
  }

  public ResponseDTO<NGTriggerResponseDTO> update(String ifMatch, @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, @NotNull String yaml,
      boolean ignoreError) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_CREATE_AND_EDIT);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", targetIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    try {
      TriggerDetails triggerDetails = ngTriggerService.fetchTriggerEntity(accountIdentifier, orgIdentifier,
          projectIdentifier, targetIdentifier, triggerIdentifier, yaml, ngTriggerEntity.get().getWithServiceV2());

      ngTriggerService.validateTriggerConfig(triggerDetails);
      triggerDetails.getNgTriggerEntity().setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
      NGTriggerEntity updatedEntity;

      if (ignoreError) {
        updatedEntity = ngTriggerService.update(triggerDetails.getNgTriggerEntity());
      } else {
        ngTriggerService.validatePipelineRef(triggerDetails);
        updatedEntity = ngTriggerService.update(triggerDetails.getNgTriggerEntity());
      }
      return ResponseDTO.newResponse(
          updatedEntity.getVersion().toString(), ngTriggerElementMapper.toResponseDTO(updatedEntity));
    } catch (InvalidTriggerYamlException e) {
      return ResponseDTO.newResponse(ngTriggerElementMapper.toErrorDTO(e));
    } catch (Exception e) {
      throw new InvalidRequestException("Failed while updating Trigger: " + e.getMessage());
    }
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<Boolean> updateTriggerStatus(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, @NotNull boolean status) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    return ResponseDTO.newResponse(ngTriggerService.updateTriggerStatus(ngTriggerEntity.get(), status));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<Boolean> delete(String ifMatch, @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier) {
    boolean triggerDeleted = ngTriggerService.delete(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    if (triggerDeleted) {
      ngTriggerEventsService.deleteTriggerEventHistory(
          accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier);
    }
    return ResponseDTO.newResponse(triggerDeleted);
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<PageResponse<NGTriggerDetailsResponseDTO>> getListForTarget(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String targetIdentifier,
      String filterQuery, int page, int size, List<String> sort, String searchTerm) {
    Criteria criteria = TriggerFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, null, searchTerm, false);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGTriggerEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    return ResponseDTO.newResponse(getNGPageResponse(ngTriggerService.list(criteria, pageRequest).map(triggerEntity -> {
      NGTriggerDetailsResponseDTO responseDTO =
          ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(triggerEntity, true, false, false);
      return responseDTO;
    })));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<NGTriggerDetailsResponseDTO> getTriggerDetails(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String triggerIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    if (!ngTriggerEntity.isPresent()) {
      return ResponseDTO.newResponse(null);
    }
    return ResponseDTO.newResponse(ngTriggerEntity.get().getVersion().toString(),
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity.get(), true, true, false));
  }

  @Timed
  @ExceptionMetered
  public RestResponse<String> generateWebhookToken() {
    return new RestResponse<>(CryptoUtils.secureRandAlphaNumString(40));
  }

  @Override
  public ResponseDTO<NGTriggerCatalogDTO> getTriggerCatalog(String accountIdentifier) {
    List<TriggerCatalogItem> triggerCatalog = ngTriggerService.getTriggerCatalog(accountIdentifier);
    return ResponseDTO.newResponse(ngTriggerElementMapper.toCatalogDTO(triggerCatalog));
  }

  @Override
  public ResponseDTO<Page<NGTriggerEventHistoryDTO>> getTriggerEventHistory(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String targetIdentifier, String triggerIdentifier,
      String searchTerm, int page, int size, List<String> sort) {
    return ngTriggerEventHistoryResource.getTriggerEventHistory(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, searchTerm, page, size, sort);
  }

  @Override
  public ResponseDTO<TriggerYamlDiffDTO> getTriggerReconciliationYamlDiff(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String targetIdentifier,
      String triggerIdentifier) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    if (!ngTriggerEntity.isPresent()) {
      throw new EntityNotFoundException(String.format("Trigger %s does not exist", triggerIdentifier));
    }
    TriggerDetails triggerDetails =
        ngTriggerService.fetchTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier,
            triggerIdentifier, ngTriggerEntity.get().getYaml(), ngTriggerEntity.get().getWithServiceV2());
    return ResponseDTO.newResponse(ngTriggerService.getTriggerYamlDiff(triggerDetails));
  }

  @Override
  @Hidden
  public ResponseDTO<NGTriggerConfigV2> getNGTriggerConfigV2() {
    return null;
  }

  @Override
  @InternalApi
  public ResponseDTO<TriggerUpdateCount> updateBranchName(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, GitMoveOperationType operationType,
      String pipelineBranchName) {
    return ResponseDTO.newResponse(ngTriggerService.updateBranchName(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, operationType, pipelineBranchName));
  }
}
