/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.jobs;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvVariablesSyncJobTest extends CategoryTest {
  private static final String TEST_ACCOUNT1 = "acc1";
  private static final String TEST_ACCOUNT2 = "acc2";
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private NamespaceService namespaceService;
  @InjectMocks private BackstageEnvVariablesSyncJob job;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testEnvSecretSync() {
    List<String> accountIds = Arrays.asList(TEST_ACCOUNT1, TEST_ACCOUNT2);
    when(namespaceService.getAccountIds()).thenReturn(accountIds);
    job.run();
    verify(backstageEnvVariableService).findAndSync(TEST_ACCOUNT1);
    verify(backstageEnvVariableService).findAndSync(TEST_ACCOUNT2);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testEnvSecretSyncErrorWithOneAccount() {
    List<String> accountIds = Arrays.asList(TEST_ACCOUNT1, TEST_ACCOUNT2);
    when(namespaceService.getAccountIds()).thenReturn(accountIds);
    doThrow(new InvalidRequestException("Failed to replace secret. Code: 403"))
        .when(backstageEnvVariableService)
        .findAndSync(TEST_ACCOUNT1);
    job.run();
    // Sync should happen for 2nd account even if there is an error in 1st account sync.
    verify(backstageEnvVariableService).findAndSync(TEST_ACCOUNT2);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
