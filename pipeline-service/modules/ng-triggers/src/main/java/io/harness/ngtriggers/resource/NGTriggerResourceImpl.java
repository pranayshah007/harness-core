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
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.exceptions.InvalidTriggerYamlException;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rest.RestResponse;
import io.harness.utils.CryptoUtils;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final NGTriggerElementMapper ngTriggerElementMapper;

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<NGTriggerResponseDTO> create(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, @NotNull String yaml, boolean ignoreError) {
    NGTriggerEntity createdEntity = null;
    try {
      TriggerDetails triggerDetails =
          ngTriggerElementMapper.toTriggerDetails(accountIdentifier, orgIdentifier, projectIdentifier, yaml);
      ngTriggerService.validateTriggerConfig(triggerDetails);

      if (ignoreError) {
        createdEntity = ngTriggerService.create(triggerDetails.getNgTriggerEntity());
      } else {
        ngTriggerService.validateInputSets(triggerDetails);
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
      throw new InvalidRequestException(String.format("Trigger %s does not exist", triggerIdentifier));
    }

    return ResponseDTO.newResponse(ngTriggerEntity.get().getVersion().toString(),
        ngTriggerEntity.map(ngTriggerElementMapper::toResponseDTO).orElse(null));
  }

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<NGTriggerResponseDTO> update(String ifMatch, @NotNull @AccountIdentifier String accountIdentifier,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String targetIdentifier, String triggerIdentifier, @NotNull String yaml,
      boolean ignoreError) {
    Optional<NGTriggerEntity> ngTriggerEntity = ngTriggerService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, false);
    if (!ngTriggerEntity.isPresent()) {
      throw new InvalidRequestException("Trigger doesn't not exists");
    }

    try {
      TriggerDetails triggerDetails = ngTriggerService.fetchTriggerEntity(
          accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, triggerIdentifier, yaml);

      ngTriggerService.validateTriggerConfig(triggerDetails);
      triggerDetails.getNgTriggerEntity().setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
      NGTriggerEntity updatedEntity;

      if (ignoreError) {
        updatedEntity = ngTriggerService.update(triggerDetails.getNgTriggerEntity());
      } else {
        ngTriggerService.validateInputSets(triggerDetails);
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
    return ResponseDTO.newResponse(ngTriggerService.delete(accountIdentifier, orgIdentifier, projectIdentifier,
        targetIdentifier, triggerIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
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

    return ResponseDTO.newResponse(getNGPageResponse(
        ngTriggerService.list(criteria, pageRequest)
            .map(triggerEntity -> ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(triggerEntity, true, false))));
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
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity.get(), true, true));
  }

  @Timed
  @ExceptionMetered
  public RestResponse<String> generateWebhookToken() {
    return new RestResponse<>(CryptoUtils.secureRandAlphaNumString(40));
  }
}
