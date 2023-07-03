/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.repositories;

import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.gitintegration.beans.CatalogInfraConnectorType;
import io.harness.idp.gitintegration.beans.CatalogRepositoryDetails;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
public class CatalogConnectorRepositoryCustomImplTest {
  @InjectMocks private CatalogConnectorRepositoryCustomImpl catalogConnectorRepositoryCustomImpl;
  @Mock SetupUsageProducer setupUsageProducer;

  @Mock private MongoTemplate mongoTemplate;

  private static final String ACCOUNT_ID = "123";
  private static final String GITHUB_IDENTIFIER = "testGithub";
  public static final String GITHUB_CONNECTOR_TYPE = "Github";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveOrUpdate() {
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity();
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogConnectorEntity.class))).thenReturn(null);
    when(mongoTemplate.save(any(CatalogConnectorEntity.class))).thenReturn(catalogConnectorEntity);
    doNothing().when(setupUsageProducer).publishConnectorSetupUsage(any(), any(), any());
    doNothing().when(setupUsageProducer).deleteConnectorSetupUsage(any(), any());
    CatalogConnectorEntity entity = catalogConnectorRepositoryCustomImpl.saveOrUpdate(catalogConnectorEntity);
    assertNotNull(entity);

    when(mongoTemplate.findOne(any(Query.class), eq(CatalogConnectorEntity.class))).thenReturn(catalogConnectorEntity);
    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(CatalogConnectorEntity.class)))
        .thenReturn(catalogConnectorEntity);
    entity = catalogConnectorRepositoryCustomImpl.saveOrUpdate(catalogConnectorEntity);
    assertNotNull(entity);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindLastUpdated() {
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity();
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogConnectorEntity.class))).thenReturn(catalogConnectorEntity);
    CatalogConnectorEntity entity = catalogConnectorRepositoryCustomImpl.findLastUpdated(ACCOUNT_ID);
    assertNotNull(entity);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindAllHostsByAccountIdentifier() {
    Criteria criteria = Criteria.where(CatalogConnectorEntity.CatalogConnectorKeys.accountIdentifier).is(ACCOUNT_ID);
    Query query = new Query(criteria);
    query.fields().include(CatalogConnectorEntity.CatalogConnectorKeys.type);
    query.fields().include(CatalogConnectorEntity.CatalogConnectorKeys.host);
    query.fields().include(CatalogConnectorEntity.CatalogConnectorKeys.delegateSelectors);
    CatalogConnectorEntity catalogConnectorEntity = getGithubConnectorEntity();
    when(mongoTemplate.find(query, CatalogConnectorEntity.class))
        .thenReturn(Collections.singletonList(catalogConnectorEntity));

    List<CatalogConnectorEntity> entities =
        catalogConnectorRepositoryCustomImpl.findAllHostsByAccountIdentifier(ACCOUNT_ID);

    assertEquals(1, entities.size());
    assertEquals(catalogConnectorEntity, entities.get(0));
  }

  private CatalogConnectorEntity getGithubConnectorEntity() {
    return CatalogConnectorEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .connectorIdentifier(GITHUB_IDENTIFIER)
        .connectorProviderType(GITHUB_CONNECTOR_TYPE)
        .type(CatalogInfraConnectorType.DIRECT)
        .catalogRepositoryDetails(new CatalogRepositoryDetails("harness-core", "develop", "/harness-services"))
        .build();
  }
}
