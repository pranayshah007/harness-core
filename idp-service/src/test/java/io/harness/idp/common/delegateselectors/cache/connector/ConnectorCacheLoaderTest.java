/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.connector;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.rule.Owner;

import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ConnectorCacheLoaderTest extends CategoryTest {
  private static final String DELEGATE_SELECTOR1 = "selector1";
  private static final String DELEGATE_SELECTOR2 = "selector2";
  private static final String DELEGATE_SELECTOR3 = "selector3";
  private static final String DELEGATE_SELECTOR4 = "selector4";
  private static final String HOST1 = "host1";
  private static final String HOST2 = "host2";
  private static final String HOST3 = "host3";
  private AutoCloseable openMocks;
  @InjectMocks private ConnectorCacheLoader cacheLoader;
  @Mock private CatalogConnectorRepository catalogConnectorRepository;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testLoad() {
    String accountIdentifier = "exampleAccount";
    List<CatalogConnectorEntity> connectors = new ArrayList<>();
    CatalogConnectorEntity connector1 = new CatalogConnectorEntity();
    connector1.setType(CatalogInfraConnectorType.PROXY);
    connector1.setHost(HOST1);
    connector1.setDelegateSelectors(new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    CatalogConnectorEntity connector2 = new CatalogConnectorEntity();
    connector2.setType(CatalogInfraConnectorType.DIRECT);
    connector2.setHost(HOST2);
    connector2.setDelegateSelectors(new HashSet<>(Arrays.asList(DELEGATE_SELECTOR3, DELEGATE_SELECTOR4)));
    connectors.add(connector1);
    connectors.add(connector2);

    when(catalogConnectorRepository.findAllHostsByAccountIdentifier(accountIdentifier)).thenReturn(connectors);

    Map<String, Set<String>> expectedHostDelegateSelectors = new HashMap<>();
    expectedHostDelegateSelectors.put(HOST1, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));

    Map<String, Set<String>> result = cacheLoader.load(accountIdentifier);

    assertEquals(expectedHostDelegateSelectors, result);
    verify(catalogConnectorRepository, times(1)).findAllHostsByAccountIdentifier(accountIdentifier);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
