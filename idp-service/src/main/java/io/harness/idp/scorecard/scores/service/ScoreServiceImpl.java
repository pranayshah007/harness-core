/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.common.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.idp.common.JacksonUtils.readValue;
import static io.harness.idp.scorecard.scorecardchecks.mappers.CheckDetailsMapper.constructExpressionFromRules;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntityTypes;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.beans.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.providers.DataSourceProvider;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderFactory;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scorecardchecks.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecardchecks.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecardchecks.repositories.CheckRepository;
import io.harness.idp.scorecard.scorecardchecks.service.ScorecardService;
import io.harness.idp.scorecard.scores.entities.ScoreEntity;
import io.harness.idp.scorecard.scores.logging.ScoreComputationLogContext;
import io.harness.idp.scorecard.scores.mappers.ScorecardGraphSummaryInfoMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardScoreMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardSummaryInfoMapper;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.logging.AutoLogContext;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityScores;
import io.harness.spec.server.idp.v1.model.Rule;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.springdata.TransactionHelper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScoreServiceImpl implements ScoreService {
  private static final String CATALOG_API_SUFFIX = "%s/idp/api/catalog/entities?%s";
  @Inject TransactionHelper transactionHelper;
  @Inject CheckRepository checkRepository;
  @Inject DataPointsRepository datapointRepository;
  @Inject DataSourceRepository datasourceRepository;
  @Inject DataSourceLocationRepository datasourceLocationRepository;
  @Inject @Named("ScoreComputer") ExecutorService executorService;
  ScorecardService scorecardService;
  DataSourceProviderFactory dataSourceProviderFactory;
  ScoreRepository scoreRepository;
  BackstageResourceClient backstageResourceClient;
  static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public void populateData(
      String checkEntities, String datapointEntities, String datasourceEntities, String datasourceLocationEntities) {
    List<CheckEntity> checks = readValue(checkEntities, CheckEntity.class);
    List<DataPointEntity> dataPoints = readValue(datapointEntities, DataPointEntity.class);
    List<DataSourceEntity> dataSources = readValue(datasourceEntities, DataSourceEntity.class);
    List<DataSourceLocationEntity> dataSourceLocations =
        readValue(datasourceLocationEntities, DataSourceLocationEntity.class);
    log.info("Converted entities json string to corresponding list<> pojo's");
    saveAll(checks, dataPoints, dataSources, dataSourceLocations);
    log.info("Populated data into checks, dataPoints, dataSources, dataSourceLocations");
  }

  @Override
  public void computeScores(
      String accountIdentifier, List<String> scorecardIdentifiers, List<String> entityIdentifiers) {
    List<ScorecardAndChecks> scorecardsAndChecks =
        scorecardService.getAllScorecardAndChecks(accountIdentifier, scorecardIdentifiers);
    if (scorecardsAndChecks.isEmpty()) {
      log.info("No scorecards configured for account: {}", accountIdentifier);
      return;
    }

    List<ScorecardFilter> filters = getAllFilters(scorecardsAndChecks);
    Set<? extends BackstageCatalogEntity> entities = getAllEntities(accountIdentifier, entityIdentifiers, filters);
    if (entities.isEmpty()) {
      log.warn("Account {} has no backstage entities matching the scorecard filters", accountIdentifier);
      return;
    }

    Map<String, Set<String>> dataPointsAndInputValues = getDataPointsAndInputValues(scorecardsAndChecks);

    CountDownLatch latch = new CountDownLatch(entities.size());
    for (BackstageCatalogEntity entity : entities) {
      executorService.submit(() -> {
        try {
          Map<String, Map<String, Object>> data = fetch(accountIdentifier, entity, dataPointsAndInputValues);
          compute(accountIdentifier, entity, scorecardsAndChecks, data);
        } finally {
          latch.countDown();
        }
      });
    }

    if (entityIdentifiers != null && !entityIdentifiers.isEmpty()) {
      try {
        if (!latch.await(30, TimeUnit.SECONDS)) {
          log.warn("Timeout waiting for threads to complete.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Interrupted while waiting for threads.");
      }
    }
  }

  @Override
  public List<ScorecardSummaryInfo> getScoresSummaryForAnEntity(String accountIdentifier, String entityIdentifier) {
    Map<String, Scorecard> scorecardIdentifierEntityMapping =
        scorecardService.getAllScorecardsAndChecksDetails(accountIdentifier)
            .stream()
            .collect(Collectors.toMap(Scorecard::getIdentifier, Function.identity()));

    Map<String, ScoreEntity> lastComputedScoresForScorecards = getScoreEntityAndScoreCardIdentifierMapping(
        scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountIdentifier, entityIdentifier)
            .getMappedResults());

    // deleting scores for deleted scorecards
    deleteScoresForDeletedScoreCards(
        accountIdentifier, scorecardIdentifierEntityMapping, lastComputedScoresForScorecards);

    return lastComputedScoresForScorecards.keySet()
        .stream()
        .filter(scoreCardIdentifier -> scorecardIdentifierEntityMapping.get(scoreCardIdentifier).isPublished())
        .map(scoreCardIdentifier
            -> ScorecardSummaryInfoMapper.toDTO(lastComputedScoresForScorecards.get(scoreCardIdentifier),
                scorecardIdentifierEntityMapping.get(scoreCardIdentifier).getName(),
                scorecardIdentifierEntityMapping.get(scoreCardIdentifier).getDescription()))
        .collect(Collectors.toList());
  }

  @Override
  public List<ScorecardGraphSummaryInfo> getScoresGraphSummaryForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifierAndScorecardIdentifier(
            accountIdentifier, entityIdentifier, scorecardIdentifier);
    return scoreEntities.stream().map(ScorecardGraphSummaryInfoMapper::toDTO).collect(Collectors.toList());
  }

  @Override
  public List<ScorecardScore> getScorecardScoreOverviewForAnEntity(String accountIdentifier, String entityIdentifier) {
    Map<String, Scorecard> scorecardIdentifierEntityMapping =
        scorecardService.getAllScorecardsAndChecksDetails(accountIdentifier)
            .stream()
            .collect(Collectors.toMap(Scorecard::getIdentifier, Function.identity()));

    Map<String, ScoreEntity> lastComputedScoresForScorecards = getScoreEntityAndScoreCardIdentifierMapping(
        scoreRepository.getAllLatestScoresByScorecardsForAnEntity(accountIdentifier, entityIdentifier)
            .getMappedResults());

    // deleting scores for deleted scorecards
    deleteScoresForDeletedScoreCards(
        accountIdentifier, scorecardIdentifierEntityMapping, lastComputedScoresForScorecards);

    return lastComputedScoresForScorecards.keySet()
        .stream()
        .filter(scorecardIdentifier -> scorecardIdentifierEntityMapping.get(scorecardIdentifier).isPublished())
        .map(scorecardIdentifier
            -> ScorecardScoreMapper.toDTO(lastComputedScoresForScorecards.get(scorecardIdentifier),
                scorecardIdentifierEntityMapping.get(scorecardIdentifier).getName(),
                scorecardIdentifierEntityMapping.get(scorecardIdentifier).getDescription()))
        .collect(Collectors.toList());
  }

  @Override
  public ScorecardSummaryInfo getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    ScorecardDetails scorecardDetails = null;
    if (scorecardIdentifier != null) {
      scorecardDetails = scorecardService.getScorecardDetails(accountIdentifier, scorecardIdentifier).getScorecard();
      if (!scorecardDetails.isPublished()) {
        throw new UnsupportedOperationException(String.format(
            "Recalibrated scores will not be calculated for unpublished scorecard - %s for entity - %s in account - %s ",
            scorecardIdentifier, entityIdentifier, accountIdentifier));
      }
    }

    computeScores(accountIdentifier,
        scorecardIdentifier == null ? Collections.emptyList() : Collections.singletonList(scorecardIdentifier),
        entityIdentifier == null ? Collections.emptyList() : Collections.singletonList(entityIdentifier));

    if (scorecardIdentifier != null) {
      ScoreEntity latestComputedScoreForScorecard = null;
      if (entityIdentifier != null) {
        latestComputedScoreForScorecard = scoreRepository.getLatestComputedScoreForEntityAndScorecard(
            accountIdentifier, entityIdentifier, scorecardIdentifier);
      }
      return ScorecardSummaryInfoMapper.toDTO(
          latestComputedScoreForScorecard, scorecardDetails.getName(), scorecardDetails.getDescription());
    }
    return null;
  }

  @Override
  public List<EntityScores> getEntityScores(String harnessAccount, ScorecardFilter filter) {
    Set<BackstageCatalogEntity> backstageCatalogEntities = getAllEntities(harnessAccount, null, List.of(filter));
    List<EntityScores> entityScores = new ArrayList<>();
    for (BackstageCatalogEntity backstageCatalogEntity : backstageCatalogEntities) {
      BackstageCatalogEntity.Metadata metadata = backstageCatalogEntity.getMetadata();
      List<ScorecardScore> scorecardScores = getScorecardScoreOverviewForAnEntity(harnessAccount, metadata.getUid());
      if (isEmpty(scorecardScores)) {
        continue;
      }
      EntityScores entity = new EntityScores();
      entity.setName(metadata.getName());
      entity.setTitle(isEmpty(metadata.getTitle()) ? metadata.getName() : metadata.getTitle());
      entity.setKind(backstageCatalogEntity.getKind());
      entity.setNamespace(metadata.getNamespace());
      entity.setScores(scorecardScores);
      entityScores.add(entity);
    }
    return entityScores;
  }

  private void saveAll(List<CheckEntity> checks, List<DataPointEntity> dataPoints, List<DataSourceEntity> dataSources,
      List<DataSourceLocationEntity> dataSourceLocations) {
    transactionHelper.performTransaction(() -> {
      checkRepository.saveAll(checks);
      datapointRepository.saveAll(dataPoints);
      datasourceRepository.saveAll(dataSources);
      datasourceLocationRepository.saveAll(dataSourceLocations);
      return null;
    });
  }

  private List<ScorecardFilter> getAllFilters(List<ScorecardAndChecks> scorecardsAndChecks) {
    return scorecardsAndChecks.stream()
        .map(scorecardAndChecks -> scorecardAndChecks.getScorecard().getFilter())
        .collect(Collectors.toList());
  }

  public Set<BackstageCatalogEntity> getAllEntities(
      String accountIdentifier, List<String> entityIdentifiers, List<ScorecardFilter> filters) {
    Set<BackstageCatalogEntity> allEntities = new HashSet<>();

    for (ScorecardFilter filter : filters) {
      StringBuilder filterStringBuilder = new StringBuilder("filter=kind=").append(filter.getKind().toLowerCase());
      if (!filter.getType().equalsIgnoreCase("all")) {
        filterStringBuilder.append(",spec.type=").append(filter.getType().toLowerCase());
      }

      for (String owner : filter.getOwners()) {
        filterStringBuilder.append(",spec.owner=").append(owner);
      }

      for (String lifecycle : filter.getLifecycle()) {
        filterStringBuilder.append(",spec.lifecycle=").append(lifecycle);
      }

      for (String tag : filter.getTags()) {
        filterStringBuilder.append(",metadata.tags=").append(tag);
      }

      try {
        String url = String.format(CATALOG_API_SUFFIX, accountIdentifier, filterStringBuilder);
        log.info("Making backstage API request: {}", url);
        Object entitiesResponse = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
        List<BackstageCatalogEntity> entities = convert(mapper, entitiesResponse, BackstageCatalogEntity.class);
        filterEntitiesByTags(entities, filter.getTags());
        if (entityIdentifiers == null || entityIdentifiers.isEmpty()) {
          allEntities.addAll(entities);
        } else {
          allEntities.addAll(entities.stream()
                                 .filter(entity -> entityIdentifiers.contains(entity.getMetadata().getUid()))
                                 .collect(Collectors.toList()));
        }
      } catch (Exception e) {
        log.error(
            "Error while fetch catalog details for account = {}, entityIdentifiers = {}, filters = {}, error = {}",
            accountIdentifier, entityIdentifiers, filters, e.getMessage(), e);
        throw new UnexpectedException("Error while fetch catalog details", e);
      }
    }
    return allEntities;
  }

  private void filterEntitiesByTags(List<BackstageCatalogEntity> entities, List<String> scorecardTags) {
    if (scorecardTags.isEmpty()) {
      return;
    }
    entities.removeIf(entity -> {
      if (entity.getMetadata().getTags().isEmpty()) {
        return true;
      }
      return !new HashSet<>(entity.getMetadata().getTags()).containsAll(scorecardTags);
    });
  }

  private Map<String, Set<String>> getDataPointsAndInputValues(List<ScorecardAndChecks> scorecardsAndChecks) {
    Map<String, Set<String>> dataPointIdentifiersAndInputValues = new HashMap<>();

    for (ScorecardAndChecks scorecardAndChecks : scorecardsAndChecks) {
      List<CheckEntity> checks = scorecardAndChecks.getChecks();
      for (CheckEntity check : checks) {
        if (check.isCustom() && !check.isHarnessManaged()) {
          // TODO: custom expressions to be handled in a different way.
          //  Maybe just return the list of dataSourceIdentifiers. Don't optimize (calling only certain DSLs) for these
          // String dataSourceIdentifier = checkEntity.getExpression().split("\\.")[0];
          log.warn("Custom expressions are not supported yet; Check {}", check.getIdentifier());
        } else {
          for (Rule rule : check.getRules()) {
            Set<String> inputValues =
                dataPointIdentifiersAndInputValues.getOrDefault(rule.getDataPointIdentifier(), new HashSet<>());
            if (StringUtils.isNotBlank(rule.getConditionalInputValue())) {
              inputValues.add(rule.getConditionalInputValue());
            }
            dataPointIdentifiersAndInputValues.put(rule.getDataPointIdentifier(), inputValues);
          }
        }
      }
    }
    return dataPointIdentifiersAndInputValues;
  }

  private Map<String, Map<String, Object>> fetch(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    try (AutoLogContext ignore1 = ScoreComputationLogContext.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .threadName(Thread.currentThread().getName())
                                      .build(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      log.info("Fetching data from provider for entity: {}", entity.getMetadata().getUid());
      Set<String> dataPointIdentifiers = dataPointsAndInputValues.keySet();
      List<DataPointEntity> dataPointEntities = datapointRepository.findByIdentifierIn(dataPointIdentifiers);
      Map<String, Map<String, Set<String>>> providerDataPoints = new HashMap<>();
      dataPointsAndInputValues.forEach((k, v) -> {
        DataPointEntity dataPointEntity =
            dataPointEntities.stream().filter(dpe -> dpe.getIdentifier().equals(k)).findFirst().orElse(null);
        assert dataPointEntity != null;
        String dataSourceIdentifier = dataPointEntity.getDataSourceIdentifier();
        if (providerDataPoints.containsKey(dataSourceIdentifier)) {
          Map<String, Set<String>> existingProviderDataPoints = providerDataPoints.get(dataSourceIdentifier);
          existingProviderDataPoints.put(k, v);
          providerDataPoints.put(dataSourceIdentifier, existingProviderDataPoints);
        } else {
          providerDataPoints.put(dataSourceIdentifier, new HashMap<>() {
            { put(k, v); }
          });
        }
      });

      Map<String, Map<String, Object>> aggregatedData = new HashMap<>();
      providerDataPoints.forEach((k, v) -> {
        DataSourceProvider provider = dataSourceProviderFactory.getProvider(k);
        try {
          Map<String, Map<String, Object>> data = provider.fetchData(accountIdentifier, entity, v);
          if (data != null) {
            aggregatedData.putAll(data);
          }
        } catch (Exception e) {
          log.warn("Error fetching data from {} provider for entity: {}", provider.getIdentifier(),
              entity.getMetadata().getUid(), e);
        }
      });
      return aggregatedData;
    }
  }

  private void compute(String accountIdentifier, BackstageCatalogEntity entity,
      List<ScorecardAndChecks> scorecardsAndChecks, Map<String, Map<String, Object>> data) {
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(data);

    for (ScorecardAndChecks scorecardAndChecks : scorecardsAndChecks) {
      ScorecardEntity scorecard = scorecardAndChecks.getScorecard();
      try (AutoLogContext ignore1 = ScoreComputationLogContext.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .scorecardIdentifier(scorecard.getIdentifier())
                                        .threadName(Thread.currentThread().getName())
                                        .build(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        if (!shouldComputeScore(scorecard.getFilter(), entity)) {
          log.info("Not computing score as the entity {} does not match the scorecard filters",
              entity.getMetadata().getUid());
          continue;
        }
        log.info("Computing score for entity: {}", entity.getMetadata().getUid());
        ScoreEntity.ScoreEntityBuilder scoreBuilder = ScoreEntity.builder()
                                                          .scorecardIdentifier(scorecard.getIdentifier())
                                                          .accountIdentifier(accountIdentifier)
                                                          .entityIdentifier(entity.getMetadata().getUid());

        int totalScore = 0;
        int totalPossibleScore = 0;
        List<CheckStatus> checkStatuses = new ArrayList<>();
        List<CheckEntity> checks = scorecardAndChecks.getChecks();

        Map<String, ScorecardEntity.Check> scorecardCheckByIdentifier = scorecard.getChecks().stream().collect(
            Collectors.toMap(ScorecardEntity.Check::getIdentifier, Function.identity()));

        for (CheckEntity check : checks) {
          CheckStatus checkStatus = new CheckStatus();
          checkStatus.setName(check.getName());
          Pair<CheckStatus.StatusEnum, String> statusAndMessage = getCheckStatusAndFailureReason(evaluator, check);
          checkStatus.setStatus(statusAndMessage.getFirst());
          if (statusAndMessage.getSecond() != null) {
            checkStatus.setReason(statusAndMessage.getSecond());
          }
          log.info("Check {}, Status : {}, Reason: {}", check.getIdentifier(), checkStatus.getStatus(),
              statusAndMessage.getSecond());

          double weightage = scorecardCheckByIdentifier.get(check.getIdentifier()).getWeightage();
          totalPossibleScore += weightage;
          totalScore += (checkStatus.getStatus().equals(CheckStatus.StatusEnum.PASS) ? 1 : 0) * weightage;
          checkStatus.setWeight((int) weightage);
          checkStatuses.add(checkStatus);
        }

        int score = totalPossibleScore == 0 ? 0 : Math.round((float) totalScore / totalPossibleScore * 100);
        scoreBuilder.checkStatus(checkStatuses);
        scoreBuilder.score(score);
        scoreBuilder.lastComputedTimestamp(System.currentTimeMillis());
        scoreRepository.save(scoreBuilder.build());
        log.info("Score computed for entity {} with score: {}", entity.getMetadata().getUid(), score);
      } catch (Exception e) {
        log.warn("Error computing score", e);
      }
    }
  }

  private boolean shouldComputeScore(ScorecardFilter filter, BackstageCatalogEntity entity) {
    String entityType = BackstageCatalogEntityTypes.getEntityType(entity);
    String entityOwner = BackstageCatalogEntityTypes.getEntityOwner(entity);
    String entityLifecycle = BackstageCatalogEntityTypes.getEntityLifecycle(entity);
    if (!filter.getKind().equalsIgnoreCase(entity.getKind())
        || (!filter.getType().equalsIgnoreCase("all") && entityType != null
            && !filter.getType().equalsIgnoreCase(entityType))
        || (!filter.getOwners().isEmpty() && !filter.getOwners().contains(entityOwner))
        || (!filter.getLifecycle().isEmpty() && !filter.getLifecycle().contains(entityLifecycle))) {
      return false;
    }
    List<BackstageCatalogEntity> entities = Arrays.asList(entity);
    filterEntitiesByTags(entities, filter.getTags());
    return !entities.isEmpty();
  }

  private Pair<CheckStatus.StatusEnum, String> getCheckStatusAndFailureReason(
      IdpExpressionEvaluator evaluator, CheckEntity checkEntity) {
    String expression = constructExpressionFromRules(
        checkEntity.getRules(), checkEntity.getRuleStrategy(), DATA_POINT_VALUE_KEY, false);
    Object value = null;
    try {
      value = evaluator.evaluateExpression(expression, RETURN_NULL_IF_UNRESOLVED);
    } catch (Exception e) {
      log.error("Expression evaluation failed. Falling back to default check behaviour", e);
    }
    if (value == null) {
      log.warn("Could not evaluate check status for {}", checkEntity.getIdentifier());
      return new Pair<>(CheckStatus.StatusEnum.valueOf(checkEntity.getDefaultBehaviour().toString()), null);
    } else {
      if (!(value instanceof Boolean)) {
        log.warn("Expected boolean assertion, got {} value for check {}", value, checkEntity.getIdentifier());
        return new Pair<>(CheckStatus.StatusEnum.valueOf(checkEntity.getDefaultBehaviour().toString()), null);
      }
      if (!(boolean) value) {
        return new Pair<>(CheckStatus.StatusEnum.FAIL, getCheckFailureReason(evaluator, checkEntity));
      }
      return new Pair<>(CheckStatus.StatusEnum.PASS, null);
    }
  }

  private String getCheckFailureReason(IdpExpressionEvaluator evaluator, CheckEntity checkEntity) {
    StringBuilder reasonBuilder = new StringBuilder();
    for (Rule rule : checkEntity.getRules()) {
      try {
        String errorMessageExpression = constructExpressionFromRules(
            Collections.singletonList(rule), checkEntity.getRuleStrategy(), ERROR_MESSAGE_KEY, true);
        Object errorMessage = evaluator.evaluateExpression(errorMessageExpression, RETURN_NULL_IF_UNRESOLVED);
        if ((errorMessage instanceof String) && !((String) errorMessage).isEmpty()) {
          reasonBuilder.append(String.format("Reason: %s", errorMessage));
        }
      } catch (Exception e) {
        log.warn("Reason expression evaluation failed", e);
      }
    }
    return reasonBuilder.toString();
  }

  private Map<String, ScoreEntity> getScoreEntityAndScoreCardIdentifierMapping(
      List<ScoreEntityByScorecardIdentifier> scoreEntityByScorecardIdentifierList) {
    return scoreEntityByScorecardIdentifierList.stream().collect(Collectors.toMap(
        ScoreEntityByScorecardIdentifier::getScorecardIdentifier, ScoreEntityByScorecardIdentifier::getScoreEntity));
  }

  private void deleteScoresForDeletedScoreCards(String accountIdentifier,
      Map<String, Scorecard> scorecardIdentifierMapping, Map<String, ScoreEntity> lastComputedScores) {
    List<String> scoreIdsToBeDeleted = new ArrayList<>();

    for (Map.Entry<String, ScoreEntity> lastComputedScore : lastComputedScores.entrySet()) {
      if (!scorecardIdentifierMapping.containsKey(lastComputedScore.getKey())) {
        scoreIdsToBeDeleted.add(lastComputedScore.getValue().getId());
      }
    }
    scoreRepository.deleteAllByAccountIdentifierAndIdIn(accountIdentifier, scoreIdsToBeDeleted);
  }
}
