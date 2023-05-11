/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.NICOLAS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class ManagerVersionsCleanUpJobTest extends WingsBaseTest {
  private static final String TEST_PRIMARY_VERSION = "testPrimaryVersion";

  @Mock private ConfigurationController configurationController;
  @Mock private HPersistence persistence;
  @Mock private AccountService accountService;
  @Inject private MainConfiguration mainConfiguration;

  private ManagerVersionsCleanUpJob managerVersionsCleanUpJob;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    managerVersionsCleanUpJob =
        new ManagerVersionsCleanUpJob(configurationController, persistence, mainConfiguration, accountService);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void run_featureFlagDisabled() {
    when(accountService.isFeatureFlagEnabled(
             FeatureName.CLEAN_UP_OLD_MANAGER_VERSIONS.toString(), Account.GLOBAL_ACCOUNT_ID))
        .thenReturn(false);

    managerVersionsCleanUpJob.run();

    verify(configurationController, times(0)).isPrimary();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void run_featureFlagEnabled() {
    when(accountService.isFeatureFlagEnabled(
             FeatureName.CLEAN_UP_OLD_MANAGER_VERSIONS.toString(), Account.GLOBAL_ACCOUNT_ID))
        .thenReturn(true);

    managerVersionsCleanUpJob.run();

    verify(configurationController, times(1)).isPrimary();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void cleanUpDelegateVersions_notPrimary() {
    when(configurationController.isPrimary()).thenReturn(false);

    managerVersionsCleanUpJob.cleanUpDelegateVersions();

    verify(persistence, times(0)).get(Account.class, Account.GLOBAL_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void cleanUpDelegateVersions_nullGlobalAccount() {
    when(configurationController.isPrimary()).thenReturn(true);
    when(persistence.get(Account.class, Account.GLOBAL_ACCOUNT_ID)).thenReturn(null);

    managerVersionsCleanUpJob.cleanUpDelegateVersions();
    verify(persistence, times(1)).get(Account.class, Account.GLOBAL_ACCOUNT_ID);
    verify(configurationController, times(0)).getPrimaryVersion();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void cleanUpDelegateVersions_nullDelegateConfiguration() {
    when(configurationController.isPrimary()).thenReturn(true);
    when(persistence.get(Account.class, Account.GLOBAL_ACCOUNT_ID))
        .thenReturn(globalAccountWithNullDelegateConfiguration());

    managerVersionsCleanUpJob.cleanUpDelegateVersions();
    verify(persistence, times(1)).get(Account.class, Account.GLOBAL_ACCOUNT_ID);
    verify(configurationController, times(0)).getPrimaryVersion();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void cleanUpDelegateVersions_noVersionsToCheck() {
    when(configurationController.isPrimary()).thenReturn(true);
    when(persistence.get(Account.class, Account.GLOBAL_ACCOUNT_ID)).thenReturn(globalAccountWithEmptyManagerVersions());
    when(configurationController.getPrimaryVersion()).thenReturn(TEST_PRIMARY_VERSION);

    managerVersionsCleanUpJob.cleanUpDelegateVersions();

    verify(persistence, times(1)).get(Account.class, Account.GLOBAL_ACCOUNT_ID);
    verify(configurationController, times(1)).getPrimaryVersion();

    verify(persistence, times(0)).createUpdateOperations(Account.class);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void cleanUpDelegateVersions_onlyWithPrimaryVersion() {
    when(configurationController.isPrimary()).thenReturn(true);
    when(persistence.get(Account.class, Account.GLOBAL_ACCOUNT_ID)).thenReturn(globalAccountOnlyWithPrimaryVersions());
    when(configurationController.getPrimaryVersion()).thenReturn(TEST_PRIMARY_VERSION);

    managerVersionsCleanUpJob.cleanUpDelegateVersions();

    verify(persistence, times(1)).get(Account.class, Account.GLOBAL_ACCOUNT_ID);
    verify(persistence, times(1)).createUpdateOperations(Account.class);
    verify(persistence, times(0)).update(any(PersistentEntity.class), any(UpdateOperations.class));
  }

  private Account globalAccountWithNullDelegateConfiguration() {
    return buildTestGlobalAccount(null);
  }

  private Account globalAccountWithEmptyManagerVersions() {
    return buildTestGlobalAccount(buildTestDelegateConfiguration(Collections.emptyList()));
  }

  private Account globalAccountOnlyWithPrimaryVersions() {
    return buildTestGlobalAccount(buildTestDelegateConfiguration(buildTestVersionListOnlyWithPrimaryVersion()));
  }

  private Account buildTestGlobalAccount(DelegateConfiguration delegateConfiguration) {
    return Account.Builder.anAccount()
        .withUuid(Account.GLOBAL_ACCOUNT_ID)
        .withDelegateConfiguration(delegateConfiguration)
        .build();
  }

  private DelegateConfiguration buildTestDelegateConfiguration(List<String> versions) {
    return DelegateConfiguration.builder().delegateVersions(versions).build();
  }

  private List<String> buildTestVersionListOnlyWithPrimaryVersion() {
    List<String> versions = new ArrayList<>();
    versions.add(TEST_PRIMARY_VERSION);

    return versions;
  }
}
