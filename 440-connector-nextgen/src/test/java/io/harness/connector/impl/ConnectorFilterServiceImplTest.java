/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.VED;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.rule.Owner;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(CDC)
public class ConnectorFilterServiceImplTest {
  @InjectMocks ConnectorFilterServiceImpl connectorFilterService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testCreateCriteriaFromConnectorFilterWithoutVersion() {
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.accountIdentifier).is("accountIdentifier");
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    criteria.and(ConnectorKeys.orgIdentifier).is("orgIdentifier");
    criteria.and(ConnectorKeys.projectIdentifier).is("projectIdentifier");
    criteria.and(ConnectorKeys.type).is("DOCKER");
    criteria.and(ConnectorKeys.categories).in(ConnectorCategory.ARTIFACTORY);

    Criteria actualCriteria = connectorFilterService.createCriteriaFromConnectorFilter("accountIdentifier",
        "orgIdentifier", "projectIdentifier", null, ConnectorType.DOCKER, ConnectorCategory.ARTIFACTORY, null, false,
        null, emptyList());

    assertThat(actualCriteria.getCriteriaObject()).isEqualTo(criteria.getCriteriaObject());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testCreateCriteriaFromConnectorFilterWithVersion() {
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.accountIdentifier).is("accountIdentifier");
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    criteria.and(ConnectorKeys.orgIdentifier).is("orgIdentifier");
    criteria.and(ConnectorKeys.projectIdentifier).is("projectIdentifier");
    criteria.and(ConnectorKeys.type).is("NEXUS");
    criteria.and(ConnectorKeys.categories).in(ConnectorCategory.ARTIFACTORY);
    criteria.and("nexusVersion").is("3.x");

    Criteria actualCriteria = connectorFilterService.createCriteriaFromConnectorFilter("accountIdentifier",
        "orgIdentifier", "projectIdentifier", null, ConnectorType.NEXUS, ConnectorCategory.ARTIFACTORY, null, false,
        "3.x", emptyList());

    assertThat(actualCriteria.getCriteriaObject()).isEqualTo(criteria.getCriteriaObject());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void createCriteriaFromConnectorIdsInFilter() {
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.accountIdentifier).is("accountIdentifier");
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    criteria.and(ConnectorKeys.orgIdentifier).is("orgIdentifier");
    criteria.and(ConnectorKeys.projectIdentifier).is("projectIdentifier");
    criteria.and(ConnectorKeys.type).is("NEXUS");
    criteria.and(ConnectorKeys.categories).in(ConnectorCategory.ARTIFACTORY);
    criteria.and("nexusVersion").is("3.x");
    List<String> connectorIds = List.of("connector1");
    criteria.and("_id").in(connectorIds);
    Criteria actualCriteria = connectorFilterService.createCriteriaFromConnectorFilter("accountIdentifier",
        "orgIdentifier", "projectIdentifier", null, ConnectorType.NEXUS, ConnectorCategory.ARTIFACTORY, null, false,
        "3.x", connectorIds);

    assertThat(actualCriteria.getCriteriaObject()).isEqualTo(criteria.getCriteriaObject());
  }
}
