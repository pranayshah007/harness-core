/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import software.wings.beans.EntityType;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats.AggregateInvocationCount;
import software.wings.dl.WingsPersistence;

import dev.morphia.AdvancedDatastore;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.aggregation.Group;
import dev.morphia.aggregation.Projection;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;

public class ServerlessTestHelper {
  public static final String ACCOUNTID = "accountid";

  @Data
  @Builder
  public static class Mocks {
    AggregationPipeline aggregationPipelineMock;
    Query queryMock;
    FieldEnd fieldEndMock;
    CriteriaContainer criteriaContainerMock;
    ServerlessInstance serverlessInstance;
    UpdateOperations updateOperationsMock;
    ServerlessInstanceStats serverlessInstanceStats;
  }

  public static Mocks setup_AggregationPipeline(WingsPersistence wingsPersistence) {
    AdvancedDatastore advancedDatastoreMock = mock(AdvancedDatastore.class);
    Query queryMock = mock(Query.class);
    final FieldEnd fieldEndMock = mock(FieldEnd.class);

    doReturn(advancedDatastoreMock).when(wingsPersistence).getDatastore(nullable(Class.class));
    doReturn(queryMock).when(wingsPersistence).createQuery(nullable(Class.class));
    doReturn(fieldEndMock).when(queryMock).field(nullable(String.class));
    final CriteriaContainer criteriaContainerMock = mock(CriteriaContainer.class);
    doReturn(criteriaContainerMock).when(queryMock).and(any());
    doReturn(criteriaContainerMock).when(queryMock).or(any());
    doReturn(fieldEndMock).when(queryMock).criteria(any());

    doReturn(queryMock).when(fieldEndMock).in(nullable(Iterable.class));
    doReturn(criteriaContainerMock).when(fieldEndMock).equal(any());
    doReturn(queryMock).when(fieldEndMock).lessThanOrEq(any());
    doReturn(criteriaContainerMock).when(fieldEndMock).greaterThanOrEq(any());
    doReturn(queryMock).when(fieldEndMock).lessThan(any());

    doReturn(queryMock).when(queryMock).project(nullable(String.class), nullable(Boolean.class));
    doReturn(queryMock).when(queryMock).order(nullable(Sort.class));

    AggregationPipeline aggregationPipelineMOck = mock(AggregationPipeline.class);
    doReturn(aggregationPipelineMOck).when(advancedDatastoreMock).createAggregation(nullable(Class.class));

    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).match(nullable(Query.class));
    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).group(anyList(), any(Group[].class));
    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).group(nullable(String.class), any(Group[].class));
    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).skip(nullable(Integer.class));
    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).limit(nullable(Integer.class));
    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).sort(any(Sort[].class));
    doReturn(aggregationPipelineMOck).when(aggregationPipelineMOck).project(any(Projection[].class));

    return Mocks.builder()
        .aggregationPipelineMock(aggregationPipelineMOck)
        .criteriaContainerMock(criteriaContainerMock)
        .fieldEndMock(fieldEndMock)
        .queryMock(queryMock)
        .build();
  }

  public static ServerlessInstanceStats getServerlessInstanceStats() {
    return new ServerlessInstanceStats(Instant.now(), ACCOUNTID, getAggregateInvocationCounts());
  }
  public static List<AggregateInvocationCount> getAggregateInvocationCounts() {
    final AggregateInvocationCount invocationCount1 = AggregateInvocationCount.builder()
                                                          .entityType(EntityType.APPLICATION)
                                                          .name("agcount")
                                                          .id("agc1")
                                                          .invocationCount(10)
                                                          .invocationCountKey(InvocationCountKey.LAST_30_DAYS)
                                                          .build();
    final AggregateInvocationCount invocationCount2 = AggregateInvocationCount.builder()
                                                          .entityType(EntityType.SERVICE)
                                                          .name("agcount2")
                                                          .id("agc2")
                                                          .invocationCount(20)
                                                          .invocationCountKey(InvocationCountKey.LAST_30_DAYS)
                                                          .build();

    final AggregateInvocationCount invocationCount3 = AggregateInvocationCount.builder()
                                                          .entityType(EntityType.APPLICATION)
                                                          .name("agcount3")
                                                          .id("agc3")
                                                          .invocationCount(20)
                                                          .invocationCountKey(InvocationCountKey.SINCE_LAST_DEPLOYED)
                                                          .build();
    final AggregateInvocationCount invocationCount4 = AggregateInvocationCount.builder()
                                                          .entityType(EntityType.APPLICATION)
                                                          .name("agcount4")
                                                          .id("appid1")
                                                          .invocationCount(100)
                                                          .invocationCountKey(InvocationCountKey.LAST_30_DAYS)
                                                          .build();

    return Arrays.asList(invocationCount1, invocationCount2, invocationCount3, invocationCount4);
  }
}
