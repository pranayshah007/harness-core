/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler;

import static io.harness.rule.OwnerRule.TEJAS;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.services.NGVaultService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.rule.Owner;
import io.harness.security.encryption.AccessType;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class NGVaultUnsetRenewalHandlerTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private NGVaultService vaultService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;

  private NGVaultUnsetRenewalHandler ngVaultUnsetRenewalHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ngVaultUnsetRenewalHandler =
        new NGVaultUnsetRenewalHandler(persistenceIteratorFactory, mongoTemplate, vaultService);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testRenewal_shouldSucceed() {
    VaultConnector vaultConnector =
        VaultConnector.builder().accessType(AccessType.TOKEN).renewalIntervalMinutes(10L).build();
    ngVaultUnsetRenewalHandler.handle(vaultConnector);
    verify(vaultService, times(1)).unsetRenewalInterval(vaultConnector);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testRenewalZeroInterval_shouldNotLookup() {
    VaultConnector vaultConnector =
        VaultConnector.builder().accessType(AccessType.TOKEN).renewalIntervalMinutes(0L).build();
    ngVaultUnsetRenewalHandler.handle(vaultConnector);
    verify(vaultService, times(0)).unsetRenewalInterval(vaultConnector);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testRenewalNotTokenAuth_shouldNotLookup() {
    VaultConnector vaultConnector =
        VaultConnector.builder().accessType(AccessType.APP_ROLE).renewalIntervalMinutes(10L).build();
    ngVaultUnsetRenewalHandler.handle(vaultConnector);
    verify(vaultService, times(0)).unsetRenewalInterval(vaultConnector);
  }
}
