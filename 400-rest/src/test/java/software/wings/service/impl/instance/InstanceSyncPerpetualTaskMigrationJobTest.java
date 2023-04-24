/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.FeatureName.MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK;
import static io.harness.rule.OwnerRule.ANKIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.InfrastructureMapping;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureMappingService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceSyncPerpetualTaskMigrationJobTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private PersistentLocker persistentLocker;
  @Mock private AppService appService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private InstanceHandlerFactory instanceHandlerFactory;
  @Mock private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Mock private InstanceSyncByPerpetualTaskHandler handler;
  @InjectMocks InstanceSyncPerpetualTaskMigrationJob job;

  private Account account1;
  private InfrastructureMapping infrastructureMapping1;

  private Account account2;
  private InfrastructureMapping infrastructureMapping2;

  @Before
  public void setUp() throws IllegalAccessException {
    AcquiredLock<?> acquiredLock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireLock(anyString(), any())).thenReturn(acquiredLock);
    FieldUtils.writeField(
        job, "featureFlagToInstanceHandlerMap", getEnablePerpetualTaskFeatureFlagsForInstanceSync(), true);

    setUpAccount();

    InstanceHandler instanceHandler =
        mock(InstanceHandler.class, withSettings().extraInterfaces(InstanceSyncByPerpetualTaskHandler.class));
    when(instanceHandlerFactory.getInstanceHandler(any())).thenReturn(instanceHandler);
    when(((InstanceSyncByPerpetualTaskHandler) instanceHandler).getFeatureFlagToEnablePerpetualTaskForInstanceSync())
        .thenReturn(Optional.of(MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK));
  }

  private Map<FeatureName, InstanceSyncByPerpetualTaskHandler> getEnablePerpetualTaskFeatureFlagsForInstanceSync() {
    Map<FeatureName, InstanceSyncByPerpetualTaskHandler> map = new HashMap<>();
    map.put(MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK, handler);
    return map;
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testFFGloballyEnabled() {
    when(featureFlagService.isGlobalEnabled(MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK)).thenReturn(true);

    job.run();

    verify(instanceSyncPerpetualTaskService, times(1)).createPerpetualTasks(infrastructureMapping1);
    verify(instanceSyncPerpetualTaskService, times(1)).createPerpetualTasks(infrastructureMapping2);

    verify(instanceSyncPerpetualTaskService, times(0)).deletePerpetualTasks(infrastructureMapping1);
    verify(instanceSyncPerpetualTaskService, times(0)).deletePerpetualTasks(infrastructureMapping2);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testFFEnabledForSelectiveAccounts() {
    when(featureFlagService.isGlobalEnabled(MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK)).thenReturn(false);
    when(featureFlagService.getAccountIds(MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK))
        .thenReturn(Collections.singleton(account2.getUuid()));

    job.run();

    verify(instanceSyncPerpetualTaskService, times(1)).deletePerpetualTasks(infrastructureMapping1);
    verify(instanceSyncPerpetualTaskService, times(1)).createPerpetualTasks(infrastructureMapping2);

    verify(instanceSyncPerpetualTaskService, times(0)).createPerpetualTasks(infrastructureMapping1);
    verify(instanceSyncPerpetualTaskService, times(0)).deletePerpetualTasks(infrastructureMapping2);
  }

  private void setUpAccount() {
    account1 = getAccount(AccountType.PAID);
    infrastructureMapping1 = mock(InfrastructureMapping.class);

    account2 = getAccount(AccountType.PAID);
    infrastructureMapping2 = mock(InfrastructureMapping.class);

    when(accountService.getAccountsWithBasicInfo(false)).thenReturn(Arrays.asList(account1, account2));

    when(appService.getAppIdsByAccountId(account1.getUuid())).thenReturn(Collections.singletonList("app1"));
    when(infrastructureMappingService.get("app1")).thenReturn(Collections.singletonList(infrastructureMapping1));

    when(appService.getAppIdsByAccountId(account2.getUuid())).thenReturn(Collections.singletonList("app2"));
    when(infrastructureMappingService.get("app2")).thenReturn(Collections.singletonList(infrastructureMapping2));
  }
}
