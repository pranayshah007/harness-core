/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.impl;
import static io.harness.polling.bean.PollingType.ARTIFACT;
import static io.harness.polling.bean.PollingType.MANIFEST;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.dto.PollingResponseDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.observer.Subject;
import io.harness.pipeline.triggers.TriggersClient;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.GitPollingPolledResponse;
import io.harness.polling.bean.ManifestPolledResponse;
import io.harness.polling.bean.PolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingDocument.PollingDocumentKeys;
import io.harness.polling.bean.PollingType;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.mapper.PollingDocumentMapper;
import io.harness.polling.service.intfc.PollingService;
import io.harness.polling.service.intfc.PollingServiceObserver;
import io.harness.repositories.polling.PollingRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class PollingServiceImpl implements PollingService {
  private int MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS = 10;
  @Inject private PollingRepository pollingRepository;
  @Inject private PollingDocumentMapper pollingDocumentMapper;
  @Inject private TriggersClient triggersClient;
  @Inject @Getter private final Subject<PollingServiceObserver> subject = new Subject<>();

  @Override
  public PollingResponseDTO save(PollingDocument pollingDocument) {
    List<String> lastPolled = new ArrayList<>();
    Long lastPollingUpdate = null;
    validatePollingDocument(pollingDocument);
    PollingDocument savedPollingDoc = pollingRepository.addSubscribersToExistingPollingDoc(
        pollingDocument.getAccountId(), pollingDocument.getOrgIdentifier(), pollingDocument.getProjectIdentifier(),
        pollingDocument.getPollingType(), pollingDocument.getPollingInfo(), pollingDocument.getSignatures(),
        pollingDocument.getSignaturesLock());
    if (savedPollingDoc != null) {
      lastPollingUpdate = savedPollingDoc.getLastModifiedPolledResponseTime() == null
          ? savedPollingDoc.getLastModifiedAt()
          : savedPollingDoc.getLastModifiedPolledResponseTime();
      lastPolled = getPolledKeys(savedPollingDoc);
      return PollingResponseDTO.builder()
          .isExistingPollingDoc(true)
          .lastPolled(lastPolled)
          .lastPollingUpdate(lastPollingUpdate)
          .pollingDocId(savedPollingDoc.getUuid())
          .build();
    }
    // savedPollingDoc will be null if we couldn't find polling doc with the same entries as pollingDocument.
    else {
      // Setting uuid as null so that on saving database generates a new uuid and does not use the old one as some other
      // trigger might still be consuming that polling document
      pollingDocument.setUuid(null);
      savedPollingDoc = pollingRepository.save(pollingDocument);
      createPerpetualTask(savedPollingDoc);
      return PollingResponseDTO.builder().isExistingPollingDoc(false).pollingDocId(savedPollingDoc.getUuid()).build();
    }
  }

  private void validatePollingDocument(PollingDocument pollingDocument) {
    if (EmptyPredicate.isEmpty(pollingDocument.getAccountId())) {
      throw new InvalidRequestException("AccountId should not be empty");
    }
    if (EmptyPredicate.isEmpty(pollingDocument.getSignatures())) {
      throw new InvalidRequestException("Signature should not be empty");
    }
  }

  @Override
  public PollingDocument get(String accountId, String pollingDocId) {
    return pollingRepository.findByUuidAndAccountId(pollingDocId, accountId);
  }

  @Override
  public List<PollingDocument> getMany(String accountId, List<String> pollingDocIds) {
    return pollingRepository.findManyByUuidsAndAccountId(pollingDocIds, accountId);
  }

  @Override
  public List<String> getUuidsBySignatures(String accountId, List<String> signatures) {
    return pollingRepository.findUuidsBySignaturesAndAccountId(signatures, accountId)
        .stream()
        .map(PollingDocument::getUuid)
        .collect(Collectors.toList());
  }

  @Override
  public List<PollingDocument> getByConnectorRef(String accountId, String connectorRef) {
    return pollingRepository.findByAccountIdAndConnectorRef(accountId, connectorRef);
  }

  @Override
  public void delete(PollingDocument pollingDocument) {
    PollingDocument savedPollDoc = pollingRepository.removeDocumentIfOnlySubscriber(
        pollingDocument.getAccountId(), pollingDocument.getUuid(), pollingDocument.getSignatures());
    // if savedPollDoc is null that means either it was not the only subscriber or this poll doc doesn't exist in db.
    if (savedPollDoc == null) {
      pollingRepository.removeSubscribersFromExistingPollingDoc(
          pollingDocument.getAccountId(), pollingDocument.getUuid(), pollingDocument.getSignatures());
    } else {
      deletePerpetualTask(savedPollDoc);
    }
  }

  @Override
  public boolean attachPerpetualTask(String accountId, String pollDocId, String perpetualTaskId) {
    UpdateResult updateResult = pollingRepository.updateSelectiveEntity(
        accountId, pollDocId, PollingDocumentKeys.perpetualTaskId, perpetualTaskId);
    return updateResult.getModifiedCount() != 0;
  }

  @Override
  public void updateFailedAttempts(String accountId, String pollingDocId, int failedAttempts) {
    pollingRepository.updateSelectiveEntity(
        accountId, pollingDocId, PollingDocumentKeys.failedAttempts, failedAttempts);
  }

  @Override
  public void updatePolledResponse(String accountId, String pollingDocId, PolledResponse polledResponse) {
    pollingRepository.updateSelectiveEntity(
        accountId, pollingDocId, PollingDocumentKeys.polledResponse, polledResponse);
  }

  @Override
  public PollingResponseDTO subscribe(PollingItem pollingItem) throws InvalidRequestException {
    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    String pollingDocId = null;
    boolean isExistingPollingDoc = false;
    List<String> lastPolled = new ArrayList<>();
    Long lastPollingUpdate = null;

    PollingDocument existingPollingDoc = null;
    if (pollingDocument.getUuid() != null) {
      existingPollingDoc = pollingRepository.findByUuidAndAccountIdAndSignature(
          pollingDocument.getUuid(), pollingDocument.getAccountId(), pollingDocument.getSignatures());
    }

    // Determine if update request
    if (existingPollingDoc == null) {
      return save(pollingDocument);
    }

    if (existingPollingDoc.getPollingInfo().equals(pollingDocument.getPollingInfo())) {
      pollingDocId = existingPollingDoc.getUuid();
      isExistingPollingDoc = true;
      lastPollingUpdate = existingPollingDoc.getLastModifiedPolledResponseTime() == null
          ? existingPollingDoc.getLastModifiedAt()
          : existingPollingDoc.getLastModifiedPolledResponseTime();
      lastPolled = getPolledKeys(existingPollingDoc);
    } else {
      delete(pollingDocument);
      // Note: This is intentional. The pollingDocId sent to us is stale, we need to set it to null so that the save
      // call creates a new pollingDoc
      pollingDocument.setUuid(null);
      pollingDocId = save(pollingDocument).getPollingDocId();
    }
    return PollingResponseDTO.builder()
        .pollingDocId(pollingDocId)
        .isExistingPollingDoc(isExistingPollingDoc)
        .lastPollingUpdate(lastPollingUpdate)
        .lastPolled(lastPolled)
        .build();
  }

  private List<String> getPolledKeys(PollingDocument pollingDocument) {
    if (pollingDocument.getPolledResponse() == null) {
      return Collections.emptyList();
    }
    if (ARTIFACT.equals(pollingDocument.getPollingType())) {
      if (((ArtifactPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys() == null) {
        return Collections.emptyList();
      }
      if (((ArtifactPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys().size()
          > MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS) {
        return new ArrayList<>(((ArtifactPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys())
            .subList(0, MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS);
      } else {
        return new ArrayList<>(((ArtifactPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys());
      }
    }
    if (MANIFEST.equals(pollingDocument.getPollingType())) {
      if (((ManifestPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys() == null) {
        return Collections.emptyList();
      }
      if (((ManifestPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys().size()
          > MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS) {
        return new ArrayList<>(((ManifestPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys())
            .subList(0, MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS);
      } else {
        return new ArrayList<>(((ManifestPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys());
      }
    }
    return Collections.emptyList();
  }

  @Override
  public boolean unsubscribe(PollingItem pollingItem) {
    /* Here we create the PollingDocument without PollingInfo, since MultiRegionArtifact triggers don't send
     this data when making an `unsubscribe` request - and this data is not needed anyway for unsubscription. */
    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocumentWithoutPollingInfo(pollingItem);
    delete(pollingDocument);
    return true;
  }

  @Override
  public void deleteAtAllScopes(Scope scope) {
    Criteria criteria =
        createScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    pollingRepository.deleteAll(criteria);
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(PollingDocumentKeys.accountId).is(accountIdentifier);
    criteria.and(PollingDocumentKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(PollingDocumentKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }

  private void createPerpetualTask(@NotNull PollingDocument pollingDocument) {
    try {
      subject.fireInform(PollingServiceObserver::onSaved, pollingDocument);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Polling Document on save for polling doc: {}",
          pollingDocument.getUuid(), e);
    }
  }

  private void resetPerpetualTask(@NotNull PollingDocument pollingDocument) {
    try {
      subject.fireInform(PollingServiceObserver::onUpdated, pollingDocument);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Polling Document on update for polling doc: {}",
          pollingDocument.getUuid(), e);
    }
  }

  private void deletePerpetualTask(@NotNull PollingDocument pollingDocument) {
    try {
      subject.fireInform(PollingServiceObserver::onDeleted, pollingDocument);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Polling Document on delete for polling doc: {}",
          pollingDocument.getUuid(), e);
    }
  }

  @Override
  public void resetPerpetualTasksForConnector(String accountId, String connectorRef) {
    List<PollingDocument> pollingDocs = getByConnectorRef(accountId, connectorRef);
    for (PollingDocument pollingDoc : pollingDocs) {
      resetPerpetualTask(pollingDoc);
    }
  }

  @Override
  public PollingInfoForTriggers getPollingInfoForTriggers(String accountId, String pollingDocId) {
    PollingDocument pollingDocument = get(accountId, pollingDocId);
    io.harness.dto.PolledResponse polledResponse = io.harness.dto.PolledResponse.builder().build();
    if (pollingDocument.getPollingType().equals(ARTIFACT)) {
      if (pollingDocument.getPolledResponse() != null) {
        polledResponse.setAllPolledKeys(
            ((ArtifactPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys());
      }
    } else if (pollingDocument.getPollingType().equals(PollingType.MANIFEST)) {
      if (pollingDocument.getPolledResponse() != null) {
        polledResponse.setAllPolledKeys(
            ((ManifestPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys());
      }
    } else if (pollingDocument.getPollingType().equals(PollingType.WEBHOOK_POLLING)) {
      if (pollingDocument.getPolledResponse() != null) {
        polledResponse.setAllPolledKeys(
            ((GitPollingPolledResponse) pollingDocument.getPolledResponse()).getAllPolledKeys());
      }
    }
    return PollingInfoForTriggers.builder()
        .pollingDocId(pollingDocId)
        .polledResponse(polledResponse)
        .perpetualTaskId(pollingDocument.getPerpetualTaskId())
        .build();
  }

  @Override
  public boolean updateTriggerPollingStatus(String accountId, List<String> signatures, boolean success,
      String errorMessage, List<String> lastCollectedVersions) {
    // Truncate `lastCollectedVersions` list to at most 10 items, in order to avoid large payloads.
    if (lastCollectedVersions != null && lastCollectedVersions.size() > MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS) {
      lastCollectedVersions = lastCollectedVersions.subList(0, MAX_COLLECTED_VERSIONS_FOR_TRIGGER_STATUS);
    }
    PollingTriggerStatusUpdateDTO statusUpdate = PollingTriggerStatusUpdateDTO.builder()
                                                     .signatures(signatures)
                                                     .success(success)
                                                     .errorMessage(errorMessage)
                                                     .lastCollectedVersions(lastCollectedVersions)
                                                     .lastCollectedTime(System.currentTimeMillis())
                                                     .build();
    try {
      return getResponse(triggersClient.updateTriggerPollingStatus(accountId, statusUpdate));
    } catch (Exception e) {
      log.error("Failed to update triggers' polling Status for accountId {}, signatures {}", accountId, signatures, e);
      return false;
    }
  }
}
