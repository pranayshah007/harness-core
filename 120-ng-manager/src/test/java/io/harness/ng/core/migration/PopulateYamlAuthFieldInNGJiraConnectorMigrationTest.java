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
import io.harness.connector.entities.embedded.jira.JiraConnector;
import io.harness.connector.entities.embedded.jira.JiraConnector.JiraConnectorBuilder;
import io.harness.connector.entities.embedded.jira.JiraUserNamePasswordAuthentication;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.ng.core.migration.background.PopulateYamlAuthFieldInNGJiraConnectorMigration;
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
public class PopulateYamlAuthFieldInNGJiraConnectorMigrationTest extends NgManagerTestBase {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private ConnectorRepository connectorRepository;

  @InjectMocks PopulateYamlAuthFieldInNGJiraConnectorMigration migration;
  private static final String accountIdentifier = "accId";
  private static final String orgIdentifier = "orgId";
  private static final String projectIdentifier = "projectID";
  private static final String identifier = "iD";

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrate() {
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(buildJiraConnector(false)));
    migration.migrate();
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    verify(connectorRepository, times(1))
        .update(criteriaArgumentCaptor.capture(), updateArgumentCaptor.capture(), any(), eq(projectIdentifier),
            eq(orgIdentifier), eq(accountIdentifier));
    Criteria expectedCriteria = Criteria.where(JiraConnector.ConnectorKeys.identifier)
                                    .is(identifier)
                                    .and(JiraConnector.ConnectorKeys.accountIdentifier)
                                    .is(accountIdentifier)
                                    .and(JiraConnector.ConnectorKeys.orgIdentifier)
                                    .is(orgIdentifier)
                                    .and(JiraConnector.ConnectorKeys.projectIdentifier)
                                    .is(projectIdentifier);
    assertEquals(expectedCriteria, criteriaArgumentCaptor.getValue());
    assertThat(updateArgumentCaptor.getValue().getUpdateObject().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMigrateShouldBeIdemPotent() {
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(buildJiraConnector(true)));
    migration.migrate();
    verifyNoMoreInteractions(connectorRepository);
  }

  private JiraConnector buildJiraConnector(boolean withNewYAML) {
    JiraConnectorBuilder jiraConnectorBuilder =
        JiraConnector.builder().jiraUrl("https://dummy.com").username("username").passwordRef("passwordRef");

    if (withNewYAML) {
      jiraConnectorBuilder.authType(JiraAuthType.USER_PASSWORD)
          .jiraAuthentication(
              JiraUserNamePasswordAuthentication.builder().username("username").passwordRef("passwordRef").build());
    }
    JiraConnector jiraConnector = jiraConnectorBuilder.build();
    jiraConnector.setAccountIdentifier(accountIdentifier);
    jiraConnector.setOrgIdentifier(orgIdentifier);
    jiraConnector.setProjectIdentifier(projectIdentifier);
    jiraConnector.setIdentifier(identifier);
    return jiraConnector;
  }
}
