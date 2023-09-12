/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.backstagebeans.BackstageCatalogComponentEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntityTypes;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.datasources.providers.DataSourceProvider;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderFactory;
import io.harness.idp.scorecard.scorecardchecks.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecardchecks.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecardchecks.service.ScorecardService;
import io.harness.idp.scorecard.scores.entities.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.Rule;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class ScoreComputerServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "123";
  private static final String FILTER_TYPE_SERVICE = "Service";
  private static final String CHECK_IDENTIFIER1 = "c1";
  private static final String CHECK_IDENTIFIER2 = "c2";
  private static final String SCORECARD_IDENTIFIER1 = "cw";
  private static final String SCORECARD_IDENTIFIER2 = "ew";
  private static final String EXPRESSION1 = "ds1.dp1==true";
  private static final String EXPRESSION2 = "ds1.dp2.v1==true";
  private static final String DATA_SOURCE_IDENTIFIER = "ds1";
  private static final String DATA_SOURCE_LOCATION_IDENTIFIER = "dsl1";
  private static final String DATA_POINT_IDENTIFIER1 = "dp1";
  private static final String DATA_POINT_IDENTIFIER2 = "dp2";
  private static final String OPERATOR1 = "==";
  private static final String OPERATOR2 = "==";
  private static final String VALUE = "true";
  private static final String ENTITY_UID1 = "uid1";
  private static final String ENTITY_UID2 = "uid2";
  private static final String OWNER = "owner1";
  private static final String TAG = "tag1";
  private static final String LIFECYCLE = "prod";
  private static final String INPUT_VALUE = "v1";
  @Mock ExecutorService executorService;
  @Mock ScorecardService scorecardService;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock DataSourceProviderFactory dataSourceProviderFactory;
  @Mock ScoreRepository scoreRepository;
  @Mock DataPointsRepository datapointRepository;
  @Mock DataSourceProvider dataSourceProvider;
  @InjectMocks ScoreComputerServiceImpl scoreComputerService;
  private Call<Object> call;
  AutoCloseable openMocks;
  static Gson gson = new Gson();
  @Captor private ArgumentCaptor<ScoreEntity> scoreCaptor;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScores() throws IOException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    ScorecardAndChecks scorecardAndChecks1 =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    ScorecardAndChecks scorecardAndChecks2 =
        ScorecardAndChecks.builder().scorecard(getMockScorecardEqualWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data1 = mockResponseData(true, false);
    Map<String, Map<String, Object>> data2 = mockResponseData(false, true);

    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(List.of(scorecardAndChecks1, scorecardAndChecks2));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyMap()))
        .thenReturn(data1)
        .thenReturn(data2);

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository, times(3)).save(scoreCaptor.capture());
    List<ScoreEntity> scores = scoreCaptor.getAllValues();
    assertEquals(3, scores.size());

    // uid1
    assertEquals(40, scores.get(0).getScore()); // custom weights scorecard; c1(2) = true, c2(3) = false
    assertEquals(50, scores.get(1).getScore()); // equal weights scorecard; c1(1) = true, c2(1) = false

    // uid2
    assertEquals(60, scores.get(2).getScore()); // custom weights scorecard; c1(2) = false, c2(3) = true
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScoresWhenNoScorecards() {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();

    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(Collections.emptyList());

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository, never()).save(any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScoresWhenNoEntities() throws IOException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.emptyList();
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = Response.success(Collections.emptyList());

    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(Collections.singletonList(scorecardAndChecks));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository, never()).save(any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testComputeScoresForAnEntity() throws IOException {
    List<String> scorecardIdentifiers = Collections.emptyList();
    List<String> entityIdentifiers = Collections.singletonList(ENTITY_UID1);
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(getMockScorecardCustomWeights()).checks(getMockChecks()).build();
    Response<Object> response = getMockServicesApiResponse();
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    DataPointEntity datapoint1 = getMockDataPoint(DATA_POINT_IDENTIFIER1, false);
    DataPointEntity datapoint2 = getMockDataPoint(DATA_POINT_IDENTIFIER2, true);
    Map<String, Map<String, Object>> data = mockResponseData(false, false);

    when(scorecardService.getAllScorecardAndChecks(ACCOUNT_ID, scorecardIdentifiers))
        .thenReturn(Collections.singletonList(scorecardAndChecks));
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(datapointRepository.findByIdentifierIn(Set.of(DATA_POINT_IDENTIFIER1, DATA_POINT_IDENTIFIER2)))
        .thenReturn(List.of(datapoint1, datapoint2));
    when(dataSourceProviderFactory.getProvider(DATA_SOURCE_IDENTIFIER)).thenReturn(dataSourceProvider);
    when(dataSourceProvider.fetchData(eq(ACCOUNT_ID), any(BackstageCatalogComponentEntity.class), anyMap()))
        .thenReturn(data);

    scoreComputerService.computeScores(ACCOUNT_ID, scorecardIdentifiers, entityIdentifiers);

    verify(scoreRepository).save(scoreCaptor.capture());
    assertEquals(0, scoreCaptor.getValue().getScore());
  }

  private Response<Object> getMockServicesApiResponse() {
    List<Map<String, Object>> services = new ArrayList<>();
    for (BackstageCatalogComponentEntity service : getMockServices()) {
      String responseString = gson.toJson(service);
      Map<String, Object> responseMap =
          gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
      services.add(responseMap);
    }
    return Response.success(services);
  }

  private List<BackstageCatalogComponentEntity> getMockServices() {
    BackstageCatalogComponentEntity service1 =
        BackstageCatalogComponentEntity.builder()
            .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
            .metadata(BackstageCatalogEntity.Metadata.builder().uid(ENTITY_UID1).tags(List.of(TAG)).build())
            .spec(BackstageCatalogComponentEntity.Spec.builder()
                      .type(FILTER_TYPE_SERVICE)
                      .owner(OWNER)
                      .lifecycle(LIFECYCLE)
                      .build())
            .build();
    BackstageCatalogComponentEntity service2 =
        BackstageCatalogComponentEntity.builder()
            .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
            .metadata(BackstageCatalogEntity.Metadata.builder().uid(ENTITY_UID2).build())
            .spec(BackstageCatalogComponentEntity.Spec.builder().type(FILTER_TYPE_SERVICE).owner(OWNER).build())
            .build();
    return List.of(service1, service2);
  }

  private List<CheckEntity> getMockChecks() {
    Rule rule1 = new Rule();
    rule1.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    rule1.setDataPointIdentifier(DATA_POINT_IDENTIFIER1);
    rule1.setOperator(OPERATOR1);
    rule1.setValue(VALUE);
    CheckEntity check1 = CheckEntity.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .identifier(CHECK_IDENTIFIER1)
                             .name(CHECK_IDENTIFIER1)
                             .expression(EXPRESSION1)
                             .ruleStrategy(CheckDetails.RuleStrategyEnum.ALL_OF)
                             .rules(Collections.singletonList(rule1))
                             .build();
    Rule rule2 = new Rule();
    rule2.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    rule2.setDataPointIdentifier(DATA_POINT_IDENTIFIER2);
    rule2.setOperator(OPERATOR2);
    rule2.setValue(VALUE);
    rule2.setConditionalInputValue(INPUT_VALUE);
    CheckEntity check2 = CheckEntity.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .identifier(CHECK_IDENTIFIER2)
                             .name(CHECK_IDENTIFIER2)
                             .expression(EXPRESSION2)
                             .ruleStrategy(CheckDetails.RuleStrategyEnum.ALL_OF)
                             .rules(Collections.singletonList(rule2))
                             .build();
    return List.of(check1, check2);
  }

  private DataPointEntity getMockDataPoint(String identifier, boolean isConditional) {
    return DataPointEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(identifier)
        .name(identifier)
        .dataSourceIdentifier(DATA_SOURCE_IDENTIFIER)
        .dataSourceLocationIdentifier(DATA_SOURCE_LOCATION_IDENTIFIER)
        .type(DataPointEntity.Type.BOOLEAN)
        .isConditional(isConditional)
        .build();
  }

  private ScorecardEntity getMockScorecardCustomWeights() {
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(BackstageCatalogEntityTypes.COMPONENT.kind);
    scorecardFilter.setType(FILTER_TYPE_SERVICE);
    scorecardFilter.setOwners(Collections.singletonList(OWNER));
    scorecardFilter.setTags(List.of());
    scorecardFilter.setLifecycle(List.of());
    ScorecardEntity.Check check1 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER1).isCustom(false).weightage(2).build();
    ScorecardEntity.Check check2 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER2).isCustom(false).weightage(3).build();
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(SCORECARD_IDENTIFIER1)
        .name(SCORECARD_IDENTIFIER1)
        .weightageStrategy(ScorecardDetails.WeightageStrategyEnum.CUSTOM)
        .filter(scorecardFilter)
        .checks(List.of(check1, check2))
        .build();
  }

  private ScorecardEntity getMockScorecardEqualWeights() {
    ScorecardFilter scorecardFilter = new ScorecardFilter();
    scorecardFilter.setKind(BackstageCatalogEntityTypes.COMPONENT.kind);
    scorecardFilter.setType(FILTER_TYPE_SERVICE);
    scorecardFilter.setOwners(Collections.singletonList(OWNER));
    scorecardFilter.setTags(List.of(TAG));
    scorecardFilter.setLifecycle(List.of(LIFECYCLE));
    ScorecardEntity.Check check1 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER1).isCustom(false).weightage(1).build();
    ScorecardEntity.Check check2 =
        ScorecardEntity.Check.builder().identifier(CHECK_IDENTIFIER2).isCustom(false).weightage(1).build();
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .identifier(SCORECARD_IDENTIFIER2)
        .name(SCORECARD_IDENTIFIER2)
        .weightageStrategy(ScorecardDetails.WeightageStrategyEnum.EQUAL_WEIGHTS)
        .filter(scorecardFilter)
        .checks(List.of(check1, check2))
        .build();
  }

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  private Map<String, Map<String, Object>> mockResponseData(boolean value1, boolean value2) {
    return Map.of(DATA_SOURCE_IDENTIFIER,
        Map.of(DATA_POINT_IDENTIFIER1, Map.of(DATA_POINT_VALUE_KEY, value1, ERROR_MESSAGE_KEY, "Invalid config"),
            DATA_POINT_IDENTIFIER2,
            Map.of(INPUT_VALUE, Map.of(DATA_POINT_VALUE_KEY, value2, ERROR_MESSAGE_KEY, "Invalid config"))));
  }
}
