/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGServiceNowConnectorMigration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.MorphiaIterator;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
public class PopulateYamlAuthFieldInNGServiceNowConnectorMigrationTest extends NgManagerTestBase {
  @Mock private HPersistence persistence;
  @Mock private ConnectorRepository connectorRepository;
  @Mock private HQuery hQuery;
  @Mock private FieldEnd fieldEnd;
  @InjectMocks PopulateYamlAuthFieldInNGServiceNowConnectorMigration migration;
  private static final String accountIdentifier = "accId";
  private static final String orgIdentifier = "orgId";
  private static final String projectIdentifier = "projectID";
  private static final String identifier = "iD";

  @Before
  public void setup() {
    when(persistence.createQuery(any(), any())).thenReturn(hQuery);
    when(hQuery.field(any())).thenReturn(fieldEnd);
    when(fieldEnd.equal(any())).thenReturn(hQuery);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrate() throws Exception {
    MorphiaIterator<ServiceNowConnector, ServiceNowConnector> morphiaIterator = mock(MorphiaIterator.class);
    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(buildServiceNowConnector(false));
    whenNew(HIterator.class).withAnyArguments().thenReturn(new HIterator(morphiaIterator));
    when(hQuery.fetch()).thenReturn(morphiaIterator);
    migration.migrate();
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    verify(connectorRepository, times(1))
        .update(criteriaArgumentCaptor.capture(), updateArgumentCaptor.capture(), any(), eq(projectIdentifier),
            eq(orgIdentifier), eq(accountIdentifier));
    Criteria expectedCriteria = Criteria.where(ServiceNowConnector.ConnectorKeys.identifier)
                                    .is(identifier)
                                    .and(ServiceNowConnector.ConnectorKeys.accountIdentifier)
                                    .is(accountIdentifier)
                                    .and(ServiceNowConnector.ConnectorKeys.orgIdentifier)
                                    .is(orgIdentifier)
                                    .and(ServiceNowConnector.ConnectorKeys.projectIdentifier)
                                    .is(projectIdentifier);
    assertEquals(expectedCriteria, criteriaArgumentCaptor.getValue());
    assertThat(updateArgumentCaptor.getValue().getUpdateObject().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrateShouldBeIdemPotent() throws Exception {
    MorphiaIterator<ServiceNowConnector, ServiceNowConnector> morphiaIterator = mock(MorphiaIterator.class);
    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(buildServiceNowConnector(true));
    whenNew(HIterator.class).withAnyArguments().thenReturn(new HIterator(morphiaIterator));

    migration.migrate();
    verifyNoMoreInteractions(connectorRepository);
  }

  private ServiceNowConnector buildServiceNowConnector(boolean withNewYAML) {
    ServiceNowConnector.ServiceNowConnectorBuilder serviceNowConnectorBuilder = ServiceNowConnector.builder()
                                                                                    .serviceNowUrl("https://dummy.com")
                                                                                    .username("username")
                                                                                    .passwordRef("passwordRef");

    if (withNewYAML) {
      serviceNowConnectorBuilder.authType(ServiceNowAuthType.USER_PASSWORD)
          .serviceNowAuthentication(ServiceNowUserNamePasswordAuthentication.builder()
                                        .username("username")
                                        .passwordRef("passwordRef")
                                        .build());
    }
    ServiceNowConnector serviceNowConnector = serviceNowConnectorBuilder.build();
    serviceNowConnector.setAccountIdentifier(accountIdentifier);
    serviceNowConnector.setOrgIdentifier(orgIdentifier);
    serviceNowConnector.setProjectIdentifier(projectIdentifier);
    serviceNowConnector.setIdentifier(identifier);
    return serviceNowConnector;
  }
}
