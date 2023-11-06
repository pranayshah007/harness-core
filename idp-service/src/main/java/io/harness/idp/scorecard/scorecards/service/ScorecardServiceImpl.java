/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.String.format;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.events.ScorecardCreateEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardDeleteEvent;
import io.harness.idp.scorecard.scorecards.events.ScorecardUpdateEvent;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardAndChecksMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardDetailsMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardMapper;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.idp.scorecard.scores.beans.BackstageCatalogEntityFacets;
import io.harness.idp.scorecard.scores.beans.ScorecardAndChecks;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.Facets;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardChecks;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ScorecardServiceImpl implements ScorecardService {
  private final ScorecardRepository scorecardRepository;
  private final CheckService checkService;
  private final SetupUsageProducer setupUsageProducer;
  private final BackstageResourceClient backstageResourceClient;

  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private final OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String TYPE_FILTER = "spec.type";
  private static final String OWNERS_FILTER = "relations.ownedBy";
  private static final String TAGS_FILTER = "metadata.tags";
  private static final String LIFECYCLE_FILTER = "spec.lifecycle";
  private static final String CATALOG_API = "%s/idp/api/catalog/entity-facets?filter=kind=%s&facet=" + TYPE_FILTER
      + "&facet=" + OWNERS_FILTER + "&facet=" + TAGS_FILTER + "&facet=" + LIFECYCLE_FILTER;

  @Inject
  public ScorecardServiceImpl(ScorecardRepository scorecardRepository, CheckService checkService,
      SetupUsageProducer setupUsageProducer, BackstageResourceClient backstageResourceClient,
      TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.scorecardRepository = scorecardRepository;
    this.checkService = checkService;
    this.setupUsageProducer = setupUsageProducer;
    this.backstageResourceClient = backstageResourceClient;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public List<Scorecard> getAllScorecardsAndChecksDetails(String accountIdentifier) {
    List<Scorecard> scorecards = new ArrayList<>();
    List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountIdentifier);
    Set<String> uniqueCheckIds = new HashSet<>();
    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      Set<String> checkIds =
          scorecardEntity.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toSet());
      uniqueCheckIds.addAll(checkIds);
    }

    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      scorecards.add(ScorecardMapper.toDTO(
          scorecardEntity, getIdentifierCheckEntityMapping(accountIdentifier, uniqueCheckIds), accountIdentifier));
    }
    return scorecards;
  }

  @Override
  public List<ScorecardAndChecks> getAllScorecardAndChecks(
      String accountIdentifier, List<String> scorecardIdentifiers) {
    List<ScorecardEntity> scorecardEntities;
    if (scorecardIdentifiers == null || scorecardIdentifiers.isEmpty()) {
      scorecardEntities = scorecardRepository.findByAccountIdentifierAndPublished(accountIdentifier, true);
    } else {
      scorecardEntities =
          scorecardRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, scorecardIdentifiers);
    }
    List<String> checkIdentifiers = scorecardEntities.stream()
                                        .flatMap(scorecardEntity -> scorecardEntity.getChecks().stream())
                                        .map(ScorecardEntity.Check::getIdentifier)
                                        .collect(Collectors.toList());
    Map<String, CheckEntity> checkEntityMap =
        checkService.getActiveChecks(accountIdentifier, checkIdentifiers)
            .stream()
            .collect(Collectors.toMap(CheckEntity::getIdentifier, Function.identity()));
    List<ScorecardAndChecks> scorecardDetailsList = new ArrayList<>();
    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      List<CheckEntity> checksList = scorecardEntity.getChecks()
                                         .stream()
                                         .filter(check -> checkEntityMap.containsKey(check.getIdentifier()))
                                         .map(check -> checkEntityMap.get(check.getIdentifier()))
                                         .collect(Collectors.toList());
      scorecardDetailsList.add(ScorecardAndChecksMapper.toDTO(scorecardEntity, checksList));
    }
    return scorecardDetailsList;
  }

  @Override
  public void saveScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    validateScorecardSaveRequest(scorecardDetailsRequest);
    validateChecks(scorecardDetailsRequest.getChecks(), accountIdentifier);

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity savedScorecardEntity =
          scorecardRepository.saveOrUpdate(ScorecardDetailsMapper.fromDTO(scorecardDetailsRequest, accountIdentifier));

      ScorecardDetailsResponse savedScorecardDetailsResponse = ScorecardDetailsMapper.toDTO(savedScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              savedScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          accountIdentifier);

      outboxService.save(new ScorecardCreateEvent(accountIdentifier, savedScorecardDetailsResponse));

      setupUsageProducer.publishScorecardSetupUsage(scorecardDetailsRequest, accountIdentifier);
      return true;
    }));
  }

  @Override
  public void updateScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    validateScorecardSaveRequest(scorecardDetailsRequest);
    validateChecks(scorecardDetailsRequest.getChecks(), accountIdentifier);

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity oldScorecardEntity = scorecardRepository.findByAccountIdentifierAndIdentifier(
          accountIdentifier, scorecardDetailsRequest.getScorecard().getIdentifier());

      ScorecardDetailsResponse oldScorecardDetails = ScorecardDetailsMapper.toDTO(oldScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              oldScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          accountIdentifier);

      ScorecardEntity updatedScorecardEntity =
          scorecardRepository.update(ScorecardDetailsMapper.fromDTO(scorecardDetailsRequest, accountIdentifier));

      ScorecardDetailsResponse updatedScorecardDetails = ScorecardDetailsMapper.toDTO(updatedScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              updatedScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          accountIdentifier);

      outboxService.save(new ScorecardUpdateEvent(accountIdentifier, updatedScorecardDetails, oldScorecardDetails));

      setupUsageProducer.deleteScorecardSetupUsage(
          accountIdentifier, scorecardDetailsRequest.getScorecard().getIdentifier());
      setupUsageProducer.publishScorecardSetupUsage(scorecardDetailsRequest, accountIdentifier);
      return true;
    }));
  }

  @Override
  public ScorecardDetailsResponse getScorecardDetails(String accountIdentifier, String identifier) {
    ScorecardEntity scorecardEntity =
        scorecardRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (scorecardEntity == null) {
      throw new InvalidRequestException(String.format("Scorecard details not found for scorecardId [%s]", identifier));
    }
    Set<String> checkIds =
        scorecardEntity.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toSet());
    return ScorecardDetailsMapper.toDTO(
        scorecardEntity, getIdentifierCheckEntityMapping(accountIdentifier, checkIds), accountIdentifier);
  }

  private void validateScorecardSaveRequest(ScorecardDetailsRequest scorecardDetailsRequest) {
    if (scorecardDetailsRequest.getScorecard().isPublished() && isEmpty(scorecardDetailsRequest.getChecks())) {
      throw new InvalidRequestException("Atleast one check should be present for publishing scorecard");
    }
  }

  private void validateChecks(List<ScorecardChecks> scorecardChecks, String harnessAccount) {
    Set<String> checkIds = scorecardChecks.stream().map(ScorecardChecks::getIdentifier).collect(Collectors.toSet());
    Map<String, CheckEntity> checkEntityMap = getIdentifierCheckEntityMapping(harnessAccount, checkIds);
    List<String> missingChecks = new ArrayList<>();
    scorecardChecks.forEach(scorecardCheck -> {
      String accountId = scorecardCheck.isCustom() ? harnessAccount : GLOBAL_ACCOUNT_ID;
      CheckEntity checkEntity = checkEntityMap.get(accountId + DOT_SEPARATOR + scorecardCheck.getIdentifier());
      if (checkEntity == null) {
        throw new InvalidRequestException(
            format("Error while saving scorecard. Could not find check %s", scorecardCheck.getIdentifier()));
      }
      if (checkEntity.isDeleted()) {
        missingChecks.add(scorecardCheck.getIdentifier());
      }
    });

    if (isNotEmpty(missingChecks)) {
      throw new InvalidRequestException(
          format("Error while saving scorecard. Please remove deleted checks %s", checkIds));
    }
  }

  @Override
  public void deleteScorecard(String accountIdentifier, String identifier) {
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ScorecardEntity toBeDeletedScorecardEntity =
          scorecardRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);

      DeleteResult deleteResult = scorecardRepository.delete(accountIdentifier, identifier);

      if (deleteResult.getDeletedCount() == 0 || toBeDeletedScorecardEntity == null) {
        throw new InvalidRequestException("Could not delete scorecard");
      }
      ScorecardDetailsResponse toBeDeletedScorecardDetails = ScorecardDetailsMapper.toDTO(toBeDeletedScorecardEntity,
          getIdentifierCheckEntityMapping(accountIdentifier,
              toBeDeletedScorecardEntity.getChecks()
                  .stream()
                  .map(ScorecardEntity.Check::getIdentifier)
                  .collect(Collectors.toSet())),
          accountIdentifier);

      outboxService.save(new ScorecardDeleteEvent(accountIdentifier, toBeDeletedScorecardDetails));

      setupUsageProducer.deleteScorecardSetupUsage(accountIdentifier, identifier);

      return true;
    }));
  }

  @Override
  public Facets getAllEntityFacets(String accountIdentifier, String kind) {
    Facets facets = new Facets();
    BackstageCatalogEntityFacets backstageCatalogEntityFacets = getEntityResponse(accountIdentifier, kind);
    populateFacets(backstageCatalogEntityFacets, facets);
    return facets;
  }

  private BackstageCatalogEntityFacets getEntityResponse(String accountIdentifier, String kind) {
    String url = String.format(CATALOG_API, accountIdentifier, kind);
    return mapper.convertValue(
        getGeneralResponse(backstageResourceClient.getCatalogEntityFacets(url)), BackstageCatalogEntityFacets.class);
  }

  private void populateFacets(BackstageCatalogEntityFacets backstageCatalogEntityFacets, Facets facets) {
    for (Map.Entry<String, List<BackstageCatalogEntityFacets.FacetType>> entry :
        backstageCatalogEntityFacets.getFacets().entrySet()) {
      switch (entry.getKey()) {
        case TYPE_FILTER:
          facets.setType(entry.getValue()
                             .stream()
                             .map(BackstageCatalogEntityFacets.FacetType::getValue)
                             .collect(Collectors.toList()));
          break;
        case OWNERS_FILTER:
          facets.setOwners(entry.getValue()
                               .stream()
                               .map(BackstageCatalogEntityFacets.FacetType::getValue)
                               .collect(Collectors.toList()));
          break;
        case TAGS_FILTER:
          facets.setTags(entry.getValue()
                             .stream()
                             .map(BackstageCatalogEntityFacets.FacetType::getValue)
                             .collect(Collectors.toList()));
          break;
        case LIFECYCLE_FILTER:
          facets.setLifecycle(entry.getValue()
                                  .stream()
                                  .map(BackstageCatalogEntityFacets.FacetType::getValue)
                                  .collect(Collectors.toList()));
          break;
      }
    }
  }

  private Map<String, CheckEntity> getIdentifierCheckEntityMapping(String accountIdentifier, Set<String> checkIds) {
    return checkService.getChecksByAccountIdAndIdentifiers(accountIdentifier, checkIds)
        .stream()
        .collect(Collectors.toMap(checkEntity
            -> checkEntity.getAccountIdentifier() + DOT_SEPARATOR + checkEntity.getIdentifier(),
            checkEntity -> checkEntity));
  }
}
