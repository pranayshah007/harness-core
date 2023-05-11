/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.ConnectorConstants.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;

@OwnedBy(DX)
@Singleton
@Slf4j
public class SecretEntityCRUDEventHandler {
  EntitySetupUsageService entitySetupUsageService;
  ConnectorService connectorService;
  private final SecretCrudService secretCrudService;

  @Inject
  public SecretEntityCRUDEventHandler(@Named(CONNECTOR_DECORATOR_SERVICE) ConnectorService connectorService,
      EntitySetupUsageService entitySetupUsageService, SecretCrudService secretCrudService) {
    this.entitySetupUsageService = entitySetupUsageService;
    this.connectorService = connectorService;
    this.secretCrudService = secretCrudService;
  }

  public boolean handleUpdate(@NotNull EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String projectIdentifier = entityChangeDTO.getProjectIdentifier().getValue();
    String orgIdentifier = entityChangeDTO.getOrgIdentifier().getValue();

    Page<EntitySetupUsageDTO> entitySetupUsageDTOS = null;
    List<EntityReference> referredByConnectorList = new ArrayList<>();
    do {
      entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(
          entitySetupUsageDTOS == null ? 0 : entitySetupUsageDTOS.getNumber() + 1, 10, accountIdentifier,
          FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
              accountIdentifier, orgIdentifier, projectIdentifier, identifier),
          EntityType.SECRETS, "");
      final List<EntityReference> pagedReferredConnectorList =
          entitySetupUsageDTOS.stream()
              .map(EntitySetupUsageDTO::getReferredByEntity)
              .filter(item -> EntityType.CONNECTORS.equals(item.getType()))
              .map(EntityDetail::getEntityRef)
              .collect(Collectors.toList());
      referredByConnectorList.addAll(pagedReferredConnectorList);
    } while (entitySetupUsageDTOS.hasNext());

    // List of Pair of accountId and heartbeat perpetual task id
    List<Pair<String, String>> connectorPerpetualTaskInfoList =
        getPerpetualTaskIdForConnectors(referredByConnectorList);
    connectorService.resetHeartbeatForReferringConnectors(connectorPerpetualTaskInfoList);
    return true;
  }

  private List<Pair<String, String>> getPerpetualTaskIdForConnectors(List<EntityReference> referredByConnectorList) {
    return referredByConnectorList.stream()
        .map(item -> {
          String identifier = item.getIdentifier();
          String accountIdentifier = item.getAccountIdentifier();
          String orgIdentifier = item.getOrgIdentifier();
          String projectIdentifier = item.getProjectIdentifier();
          String heartBeatPerpetualTaskId = connectorService.getHeartbeatPerpetualTaskId(
              accountIdentifier, orgIdentifier, projectIdentifier, identifier);
          return Pair.of(accountIdentifier, heartBeatPerpetualTaskId);
        })
        .collect(Collectors.toList());
  }

  public boolean deleteAssociatedSecrets(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<SecretResponseWrapper> secretResponseWrappers =
        fetchAllSecretsInGivenScope(accountIdentifier, orgIdentifier, projectIdentifier);
    List<String> secretIdentifiers = secretResponseWrappers.stream()
                                         .map(SecretResponseWrapper::getSecret)
                                         .map(SecretDTOV2::getIdentifier)
                                         .collect(Collectors.toList());
    if (!secretIdentifiers.isEmpty()) {
      secretCrudService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifiers);
    }
    return true;
  }

  private List<SecretResponseWrapper> fetchAllSecretsInGivenScope(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Page<SecretResponseWrapper> pagedSecretList = null;
    List<SecretResponseWrapper> secretList = new ArrayList<>();
    do {
      pagedSecretList = secretCrudService.list(accountIdentifier, orgIdentifier, projectIdentifier, null, null, false,
          null, null, false,
          PageRequest.builder()
              .pageSize(10)
              .pageIndex(pagedSecretList == null ? 0 : pagedSecretList.getNumber() + 1)
              .build());
      secretList.addAll(pagedSecretList.stream().collect(Collectors.toList()));
    } while (pagedSecretList.hasNext());
    return secretList;
  }
}
