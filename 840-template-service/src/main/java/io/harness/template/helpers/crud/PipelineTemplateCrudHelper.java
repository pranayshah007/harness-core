/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers.crud;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_INPUTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.contracts.service.EntityReferenceErrorResponse;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.contracts.service.EntityReferenceResponseWrapper;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc;
import io.harness.pms.contracts.service.ErrorMetadata;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.template.entity.TemplateEntity;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineTemplateCrudHelper implements TemplateCrudHelper {
  private static final String FQN_SEPARATOR = ".";
  PmsGitSyncHelper pmsGitSyncHelper;
  EntityReferenceServiceGrpc.EntityReferenceServiceBlockingStub entityReferenceServiceBlockingStub;

  @Override
  public boolean supportsReferences() {
    return true;
  }

  @Override
  public List<EntityDetailProtoDTO> getReferences(TemplateEntity templateEntity, String entityYaml) {
    try {
      EntityReferenceRequest.Builder entityReferenceRequestBuilder =
          EntityReferenceRequest.newBuilder()
              .setYaml(entityYaml)
              .setAccountIdentifier(templateEntity.getAccountIdentifier());
      if (isNotEmpty(templateEntity.getOrgIdentifier())) {
        entityReferenceRequestBuilder.setOrgIdentifier(templateEntity.getOrgIdentifier());
      }
      if (isNotEmpty(templateEntity.getProjectIdentifier())) {
        entityReferenceRequestBuilder.setProjectIdentifier(templateEntity.getProjectIdentifier());
      }
      ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
      if (gitSyncBranchContext != null) {
        entityReferenceRequestBuilder.setGitSyncBranchContext(gitSyncBranchContext);
      }
      EntityReferenceResponseWrapper referenceResponse =
          entityReferenceServiceBlockingStub.getReferences(entityReferenceRequestBuilder.build());

      checkAndThrowExceptionOnErrorResponse(templateEntity, referenceResponse);

      EntityReferenceResponse response = referenceResponse.getReferenceResponse();
      templateEntity.setModules(new HashSet<>(response.getModuleInfoList()));
      return correctFQNsOfReferredEntities(response.getReferredEntitiesList(), templateEntity.getTemplateEntityType());

    } catch (InvalidIdentifierRefException ex) {
      log.error("Error occurred while calculating template references {}", ex.getMessage());
      String scope = String.valueOf(templateEntity.getTemplateScope());
      throw new InvalidIdentifierRefException(String.format(
          "Unable to save to %s. Template can be saved to %s only when all the referenced entities are available in the scope.",
          scope, scope));
    }
  }

  @VisibleForTesting
  List<EntityDetailProtoDTO> correctFQNsOfReferredEntities(
      List<EntityDetailProtoDTO> referredEntities, TemplateEntityType templateEntityType) {
    List<EntityDetailProtoDTO> referredEntitiesWithModifiedFqn = new ArrayList<>();
    referredEntities.forEach(referredEntity -> {
      if (referredEntity.getIdentifierRef() != null && referredEntity.getIdentifierRef().getMetadataMap() != null) {
        String fqn = referredEntity.getIdentifierRef().getMetadataMap().get(PreFlightCheckMetadata.FQN);
        if (isEmpty(fqn)) {
          // FQN should never be empty. Let's skip this referred entity.
          return;
        }
        Map<String, String> metadata = new HashMap<>(referredEntity.getIdentifierRef().getMetadataMap());
        switch (templateEntityType) {
          case STEP_TEMPLATE:
            fqn = TEMPLATE_INPUTS + FQN_SEPARATOR + fqn;
            break;
          default:
            fqn = replaceBaseIdentifierInFQNWithTemplateInputs(fqn);
        }
        metadata.put(PreFlightCheckMetadata.FQN, fqn);
        IdentifierRefProtoDTO identifierRefProtoDTO =
            referredEntity.getIdentifierRef().toBuilder().clearMetadata().putAllMetadata(metadata).build();
        referredEntitiesWithModifiedFqn.add(
            referredEntity.toBuilder().clearIdentifierRef().setIdentifierRef(identifierRefProtoDTO).build());
      }
    });
    return referredEntitiesWithModifiedFqn;
  }

  private String replaceBaseIdentifierInFQNWithTemplateInputs(String fqn) {
    int indexOfFirstDot = fqn.indexOf(FQN_SEPARATOR);
    if (indexOfFirstDot != -1) {
      return TEMPLATE_INPUTS + fqn.substring(indexOfFirstDot);
    }
    return fqn;
  }

  private void checkAndThrowExceptionOnErrorResponse(
      TemplateEntity templateEntity, EntityReferenceResponseWrapper referenceResponse) {
    if (referenceResponse.getResponseCase() == EntityReferenceResponseWrapper.ResponseCase.ERRORRESPONSE) {
      EntityReferenceErrorResponse errorResponse = referenceResponse.getErrorResponse();
      List<String> errorMessages = new ArrayList<>();
      if (EmptyPredicate.isNotEmpty(errorResponse.getErrorMetadataList())) {
        for (ErrorMetadata errorMetadata : errorResponse.getErrorMetadataList()) {
          errorMessages.add(errorMetadata.getErrorMessage());
          if (String.valueOf(ErrorCode.INVALID_IDENTIFIER_REF).equals(errorMetadata.getWingsExceptionErrorCode())) {
            throw new InvalidIdentifierRefException(errorMetadata.getErrorMessage());
          }
        }
      }
      throw new NGTemplateException(
          String.format("Exception in calculating references for template with identifier %s and version label %s: %s",
              templateEntity.getIdentifier(), templateEntity.getVersionLabel(), String.join(", ", errorMessages)));
    }
  }
}
