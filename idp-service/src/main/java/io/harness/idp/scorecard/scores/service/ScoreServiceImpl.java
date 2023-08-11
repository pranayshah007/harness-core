/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.expression.common.ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
import static io.harness.idp.common.JacksonUtils.convert;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogEntityTypes;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.beans.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.providers.DataSourceProvider;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderFactory;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scorecards.beans.ScorecardCheckFullDetails;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entities.ScoreEntity;
import io.harness.idp.scorecard.scores.mappers.ScorecardGraphSummaryInfoMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardScoreMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardSummaryInfoMapper;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.Rule;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;
import io.harness.springdata.TransactionHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScoreServiceImpl implements ScoreService {
  private static final String CATALOG_API_SUFFIX = "%s/idp/api/catalog/entities?filter=%s";
  @Inject TransactionHelper transactionHelper;
  @Inject CheckRepository checkRepository;
  @Inject DataPointsRepository datapointRepository;
  @Inject DataSourceRepository datasourceRepository;
  @Inject DataSourceLocationRepository datasourceLocationRepository;
  ScorecardService scorecardService;
  DataSourceProviderFactory dataSourceProviderFactory;
  ScoreRepository scoreRepository;
  BackstageResourceClient backstageResourceClient;
  static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Override
  public void populateData(
      String checkEntities, String datapointEntities, String datasourceEntities, String datasourceLocationEntities) {
    List<CheckEntity> checks = convert(checkEntities, CheckEntity.class);
    List<DataPointEntity> dataPoints = convert(datapointEntities, DataPointEntity.class);
    List<DataSourceEntity> dataSources = convert(datasourceEntities, DataSourceEntity.class);
    List<DataSourceLocationEntity> dataSourceLocations =
        convert(datasourceLocationEntities, DataSourceLocationEntity.class);
    log.info("Converted entities json string to corresponding list<> pojo's");
    saveAll(checks, dataPoints, dataSources, dataSourceLocations);
    log.info("Populated data into checks, dataPoints, dataSources, dataSourceLocations");
  }

  @Override
  public void computeScores(
      String accountIdentifier, List<String> scorecardIdentifiers, List<String> entityIdentifiers) {
    List<ScorecardCheckFullDetails> scorecards =
        scorecardService.getAllScorecardCheckFullDetails(accountIdentifier, scorecardIdentifiers);
    if (scorecards.isEmpty()) {
      log.info("No scorecards configured for account: {}", accountIdentifier);
      return;
    }

    List<ScorecardFilter> filters = getAllFilters(scorecards);
    List<? extends BackstageCatalogEntity> entities = getAllEntities(accountIdentifier, entityIdentifiers, filters);
    if (entities.isEmpty()) {
      log.info("Account {} has no backstage entities", accountIdentifier);
      return;
    }

    Map<String, Set<String>> dataPointsAndInputValues = getDataPointsAndInputValues(scorecards);

    for (BackstageCatalogEntity entity : entities) {
      Map<String, Map<String, Object>> data = fetch(accountIdentifier, entity, dataPointsAndInputValues);
      compute(accountIdentifier, entity, scorecards, data);
    }
  }

  private List<ScorecardFilter> getAllFilters(List<ScorecardCheckFullDetails> scorecards) {
    return scorecards.stream().map(scorecard -> scorecard.getScorecard().getFilter()).collect(Collectors.toList());
  }

  @Override
  public List<ScorecardSummaryInfo> getScoresSummaryForAnEntity(String accountIdentifier, String entityIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifier(accountIdentifier, entityIdentifier);
    Map<String, String> scoreCardIdentifierNameMapping =
        scorecardService.getAllScorecardsAndChecksDetails(accountIdentifier)
            .stream()
            .collect(Collectors.toMap(Scorecard::getIdentifier, Scorecard::getName));
    return scoreEntities.stream()
        .map(scoreEntity
            -> ScorecardSummaryInfoMapper.toDTO(
                scoreEntity, scoreCardIdentifierNameMapping.get(scoreEntity.getScorecardIdentifier())))
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
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifier(accountIdentifier, entityIdentifier);
    Map<String, Scorecard> scorecardIdentifierEntityMapping =
        scorecardService.getAllScorecardsAndChecksDetails(accountIdentifier)
            .stream()
            .collect(Collectors.toMap(Scorecard::getIdentifier, Function.identity()));
    return scoreEntities.stream()
        .map(scoreEntity
            -> ScorecardScoreMapper.toDTO(scoreEntity,
                scorecardIdentifierEntityMapping.get(scoreEntity.getScorecardIdentifier()).getName(),
                scorecardIdentifierEntityMapping.get(scoreEntity.getScorecardIdentifier()).getDescription()))
        .collect(Collectors.toList());
  }

  @Override
  public ScorecardSummaryInfo getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifierAndScorecardIdentifier(
            accountIdentifier, entityIdentifier, scorecardIdentifier);
    if (scoreEntities.size() > 0) {
      scoreEntities.sort(Comparator.comparing(ScoreEntity::getLastComputedTimestamp));
      ScoreEntity scoreEntity = scoreEntities.get(scoreEntities.size() - 1);
      return ScorecardSummaryInfoMapper.toDTO(scoreEntity,
          scorecardService.getScorecardDetails(accountIdentifier, scoreEntity.getScorecardIdentifier())
              .getScorecard()
              .getName());
    }
    return new ScorecardSummaryInfo();
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

  public List<? extends BackstageCatalogEntity> getAllEntities(
      String accountIdentifier, List<String> entityIdentifiers, List<ScorecardFilter> filters) {
    List<BackstageCatalogEntity> allEntities = new ArrayList<>();

    Map<String, String> filterParamToKindMap = new HashMap<>();
    for (ScorecardFilter filter : filters) {
      StringBuilder filterStringBuilder = new StringBuilder();
      filterStringBuilder.append("kind=").append(filter.getKind().toLowerCase());
      if (!filter.getType().equals("All")) {
        filterStringBuilder.append(",spec.type=").append(filter.getType().toLowerCase());
      }
      filterParamToKindMap.put(filterStringBuilder.toString(), filter.getKind());
    }

    for (Map.Entry<String, String> entry : filterParamToKindMap.entrySet()) {
      try {
        String filterString = entry.getKey();
        String filterKind = entry.getValue();
        String url = String.format(CATALOG_API_SUFFIX, accountIdentifier, filterString);
        Object entitiesResponse = getGeneralResponse(backstageResourceClient.getCatalogEntities(url));
        TypeReference<List<BackstageCatalogEntity>> typeReference =
            BackstageCatalogEntityTypes.getTypeReference(filterKind);
        List<BackstageCatalogEntity> entities = mapper.convertValue(entitiesResponse, typeReference);
        if (entityIdentifiers.isEmpty()) {
          allEntities.addAll(entities);
        } else {
          allEntities.addAll(entities.stream()
                                 .filter(entity -> entityIdentifiers.contains(entity.getMetadata().getUid()))
                                 .collect(Collectors.toList()));
        }
      } catch (Exception e) {
        throw new RuntimeException("Error while fetch catalog details", e);
      }
    }
    return allEntities;
  }

  private Map<String, Set<String>> getDataPointsAndInputValues(
      List<ScorecardCheckFullDetails> scorecardCheckFullDetailsList) {
    Map<String, Set<String>> dataPointIdentifiersAndInputValues = new HashMap<>();

    for (ScorecardCheckFullDetails scorecardCheckFullDetails : scorecardCheckFullDetailsList) {
      List<CheckEntity> checks = scorecardCheckFullDetails.getChecks();
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
            inputValues.add(rule.getConditionalInputValue());
            dataPointIdentifiersAndInputValues.put(rule.getDataPointIdentifier(), inputValues);
          }
        }
      }
    }
    return dataPointIdentifiersAndInputValues;
  }

  private Map<String, Map<String, Object>> fetch(
      String accountIdentifier, BackstageCatalogEntity entity, Map<String, Set<String>> dataPointsAndInputValues) {
    Map<String, Map<String, Object>> aggregatedData = new HashMap<>();
    for (DataSourceProvider provider : dataSourceProviderFactory.getProviders()) {
      Map<String, Map<String, Object>> data = provider.fetchData(accountIdentifier, entity, dataPointsAndInputValues);
      if (data != null) {
        aggregatedData.putAll(data);
      }
    }
    return aggregatedData;
  }

  private void compute(String accountIdentifier, BackstageCatalogEntity entity,
      List<ScorecardCheckFullDetails> scorecardCheckFullDetailsList, Map<String, Map<String, Object>> data) {
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(data);

    for (ScorecardCheckFullDetails scorecardCheckFullDetails : scorecardCheckFullDetailsList) {
      ScorecardEntity scorecard = scorecardCheckFullDetails.getScorecard();
      if (!shouldComputeScore(scorecard.getFilter(), entity)) {
        return;
      }

      log.info("Calculating score for scorecard: {}, account: {}", scorecard.getIdentifier(), accountIdentifier);

      ScoreEntity.ScoreEntityBuilder scoreBuilder = ScoreEntity.builder()
                                                        .scorecardIdentifier(scorecard.getIdentifier())
                                                        .accountIdentifier(accountIdentifier)
                                                        .entityIdentifier(entity.getMetadata().getUid());

      int totalScore = 0;
      int totalPossibleScore = 0;
      List<CheckStatus> checkStatuses = new ArrayList<>();
      List<CheckEntity> checks = scorecardCheckFullDetails.getChecks();

      Map<String, ScorecardEntity.Check> scorecardCheckByIdentifier = scorecard.getChecks().stream().collect(
          Collectors.toMap(ScorecardEntity.Check::getIdentifier, Function.identity()));

      for (CheckEntity check : checks) {
        log.info("Evaluating check status for: {}, account: {}", check.getIdentifier(), accountIdentifier);

        CheckStatus checkStatus = new CheckStatus();
        checkStatus.setName(check.getName());
        checkStatus.setStatus(getCheckStatus(evaluator, check));
        checkStatuses.add(checkStatus);
        log.info("Check status for {} : {}; Account: {} ", check.getIdentifier(), checkStatus, accountIdentifier);

        double weightage = scorecardCheckByIdentifier.get(check.getIdentifier()).getWeightage();
        totalPossibleScore += weightage;
        totalScore += (checkStatus.getStatus().equals("PASS") ? 1 : 0) * weightage;
      }

      scoreBuilder.checkStatus(checkStatuses);
      scoreBuilder.score(totalPossibleScore == 0 ? 0 : Math.round((float) totalScore / totalPossibleScore * 100));
      scoreBuilder.lastComputedTimestamp(System.currentTimeMillis());
      scoreRepository.save(scoreBuilder.build());
    }
  }

  private boolean shouldComputeScore(ScorecardFilter filter, BackstageCatalogEntity entity) {
    if (!filter.getKind().equalsIgnoreCase(entity.getKind())) {
      return false;
    }
    String type = BackstageCatalogEntityTypes.getEntityType(entity);
    if (type == null) {
      return true;
    }
    return filter.getType().equalsIgnoreCase("All") || filter.getType().equalsIgnoreCase(type);
  }

  private String getCheckStatus(IdpExpressionEvaluator evaluator, CheckEntity checkEntity) {
    Object value = evaluator.evaluateExpression(checkEntity.getExpression(), RETURN_NULL_IF_UNRESOLVED);
    if (value == null) {
      log.warn("Could not evaluate check status for {}", checkEntity.getIdentifier());
      return checkEntity.getDefaultBehaviour().toString();
    } else {
      if (!(value instanceof Boolean)) {
        throw new InvalidRequestException(String.format("Expected boolean assertion, got %s value", value));
      }
      // TODO: Some issue with open api, it's not generating enum. Need to update this later
      return (boolean) value ? "PASS" : "FAIL";

      // TODO: Since we are evaluating the check as a whole, we can't find the dynamic reason
      //  (check failed because the value x was less the the threshold y)
    }
  }
}
