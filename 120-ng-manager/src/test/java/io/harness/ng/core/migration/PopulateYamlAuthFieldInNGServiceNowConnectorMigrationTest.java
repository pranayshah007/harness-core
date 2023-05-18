/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.rule.OwnerRule.NAMANG;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector.ServiceNowConnectorBuilder;
import io.harness.connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGServiceNowConnectorMigration;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
public class PopulateYamlAuthFieldInNGServiceNowConnectorMigrationTest extends NgManagerTestBase {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private ConnectorRepository connectorRepository;

  @InjectMocks PopulateYamlAuthFieldInNGServiceNowConnectorMigration migration;
  private static final String accountIdentifier = "accId";
  private static final String orgIdentifier = "orgId";
  private static final String projectIdentifier = "projectID";
  private static final String identifier = "iD";

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrate() {
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(buildServiceNowConnector(false)));
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
  public void testMigrateShouldBeIdemPotent() {
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(buildServiceNowConnector(true)));
    migration.migrate();
    verifyNoMoreInteractions(connectorRepository);
  }

  private ServiceNowConnector buildServiceNowConnector(boolean withNewYAML) {
    ServiceNowConnectorBuilder serviceNowConnectorBuilder = ServiceNowConnector.builder()
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
