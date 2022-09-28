/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.stats;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.service.impl.instance.ServerlessTestHelper.getAggregateInvocationCounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.dl.WingsPersistence;
import software.wings.resources.stats.model.ServerlessInstanceTimeline;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.instance.ServerlessTestHelper;
import software.wings.service.impl.instance.ServerlessTestHelper.Mocks;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.instance.ServerlessDashboardService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.MorphiaIterator;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

public class ServerlessInstanceStatServiceImplTest extends CategoryTest {
  @Mock private WingsPersistence wingsPersistence;
  @Mock private ServerlessDashboardService serverlessDashboardService;
  @Mock private UserService userService;

  @InjectMocks @Inject @Spy ServerlessInstanceStatServiceImpl serverlessInstanceStatService;

  public static final String ACCOUNTID = "accountid";
  public static final String APPID_1 = "appid1";
  public static final String SERVICEID = "serviceid";
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_save() {
    setup_wingspersistence();

    final boolean save = serverlessInstanceStatService.save(
        new ServerlessInstanceStats(Instant.now(), ACCOUNTID, getAggregateInvocationCounts()));
    verify(wingsPersistence, times(1)).save(any(ServerlessInstanceStats.class));
    doReturn(null).when(wingsPersistence).save(any(ServerlessInstanceStats.class));
    final boolean save1 = serverlessInstanceStatService.save(
        new ServerlessInstanceStats(Instant.now(), ACCOUNTID, getAggregateInvocationCounts()));
    assertThat(save1).isFalse();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getLastSnapshotTime() {
    final Mocks mocks = setup_wingspersistence();
    doReturn(Collections.singletonList(getServerlessInstanceStats()))
        .when(mocks.getQueryMock())
        .asList(any(FindOptions.class));
    final Instant lastSnapshotTime = serverlessInstanceStatService.getLastSnapshotTime(ACCOUNTID);
    assertThat(lastSnapshotTime).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getFirstSnapshotTime() {
    final Mocks mocks = setup_wingspersistence();
    doReturn(Collections.singletonList(getServerlessInstanceStats()))
        .when(mocks.getQueryMock())
        .asList(any(FindOptions.class));
    final Instant lastSnapshotTime = serverlessInstanceStatService.getFirstSnapshotTime(ACCOUNTID);
    assertThat(lastSnapshotTime).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_aggregate() {
    final Mocks mocks = setup_AggregationPipeline();
    final Query queryMock = mocks.getQueryMock();
    final MorphiaIterator morphiaIteratorMock = mock(MorphiaIterator.class);
    doReturn(morphiaIteratorMock).when(queryMock).fetch();
    doReturn(getServerlessInstanceStats()).when(morphiaIteratorMock).next();
    doReturn(true).doReturn(false).when(morphiaIteratorMock).hasNext();
    doReturn(morphiaIteratorMock).when(queryMock).fetch();
    doReturn(Collections.emptySet())
        .when(serverlessDashboardService)
        .getDeletedAppIds(anyString(), anyLong(), anyLong());

    final User user = new User();
    final UserRequestContext userRequestContext = UserRequestContext.builder().build();
    userRequestContext.setAppIdFilterRequired(true);
    userRequestContext.setAppIds(ImmutableSet.of(APPID_1));
    userRequestContext.setUserPermissionInfo(UserPermissionInfo.builder().build());
    UserThreadLocal.set(user);
    doReturn(true).when(userService).isAccountAdmin(anyString());

    user.setUserRequestContext(userRequestContext);

    final ServerlessInstanceTimeline aggregate = serverlessInstanceStatService.aggregate(
        ACCOUNTID, Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), InvocationCountKey.LAST_30_DAYS);
    assertThat(aggregate.getPoints().size()).isEqualTo(1);
    assertThat(aggregate.getPoints().get(0).getTotalInvocationCount()).isEqualTo(100);
  }

  private ServerlessInstanceStats getServerlessInstanceStats() {
    return ServerlessTestHelper.getServerlessInstanceStats();
  }

  private Mocks setup_AggregationPipeline() {
    final Mocks mocks = ServerlessTestHelper.setup_AggregationPipeline(wingsPersistence);
    Query queryMock = mocks.getQueryMock();
    doReturn(queryMock).when(mocks.getFieldEndMock()).greaterThanOrEq(any());

    final ServerlessInstanceStats serverlessInstanceStats = getServerlessInstanceStats();
    doReturn(serverlessInstanceStats).when(wingsPersistence).get(eq(ServerlessInstanceStats.class), anyString());
    doReturn("instanceid").when(wingsPersistence).save(any(ServerlessInstanceStats.class));
    doReturn(serverlessInstanceStats)
        .when(wingsPersistence)
        .getWithAppId(eq(ServerlessInstanceStats.class), anyString(), anyString());
    doReturn(queryMock).when(wingsPersistence).createQuery(ServerlessInstanceStats.class);
    doReturn(queryMock).when(queryMock).filter(anyString(), any());
    final UpdateOperations updateOperationsMock = mock(UpdateOperations.class);
    doReturn(updateOperationsMock).when(wingsPersistence).createUpdateOperations(ServerlessInstanceStats.class);
    doReturn(updateOperationsMock).when(updateOperationsMock).set(anyString(), any());
    doReturn(updateOperationsMock).when(updateOperationsMock).unset(anyString());

    doReturn(mock(UpdateResults.class)).when(wingsPersistence).update(any(Query.class), any(UpdateOperations.class));
    doReturn(serverlessInstanceStats).when(wingsPersistence).getWithAppId(any(Class.class), anyString(), anyString());
    doReturn(mock(PageResponse.class)).when(wingsPersistence).query(any(Class.class), any(PageRequest.class));

    return mocks;
  }

  private Mocks setup_wingspersistence() {
    final ServerlessInstanceStats serverlessInstanceStats = getServerlessInstanceStats();
    doReturn(serverlessInstanceStats).when(wingsPersistence).get(eq(ServerlessInstanceStats.class), anyString());
    doReturn("instanceid").when(wingsPersistence).save(any(ServerlessInstanceStats.class));
    doReturn(serverlessInstanceStats)
        .when(wingsPersistence)
        .getWithAppId(eq(ServerlessInstanceStats.class), anyString(), anyString());
    Query queryMock = mock(Query.class);
    doReturn(queryMock).when(wingsPersistence).createQuery(ServerlessInstanceStats.class);
    doReturn(queryMock).when(queryMock).filter(anyString(), any());
    final UpdateOperations updateOperationsMock = mock(UpdateOperations.class);
    doReturn(updateOperationsMock).when(wingsPersistence).createUpdateOperations(ServerlessInstanceStats.class);
    doReturn(updateOperationsMock).when(updateOperationsMock).set(anyString(), any());
    doReturn(updateOperationsMock).when(updateOperationsMock).unset(anyString());
    doReturn(queryMock).when(queryMock).project(anyString(), anyBoolean());
    doReturn(queryMock).when(queryMock).order(any(Sort.class));
    doReturn(mock(UpdateResults.class)).when(wingsPersistence).update(any(Query.class), any(UpdateOperations.class));
    doReturn(serverlessInstanceStats).when(wingsPersistence).getWithAppId(any(Class.class), anyString(), anyString());
    doReturn(mock(PageResponse.class)).when(wingsPersistence).query(any(Class.class), any(PageRequest.class));

    return Mocks.builder()
        .serverlessInstanceStats(serverlessInstanceStats)
        .queryMock(queryMock)
        .updateOperationsMock(updateOperationsMock)
        .build();
  }
}
