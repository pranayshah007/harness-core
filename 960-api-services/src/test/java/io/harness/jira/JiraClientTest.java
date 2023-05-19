/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(CDC)
@RunWith(PowerMockRunner.class)
@PrepareForTest({JiraClient.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class JiraClientTest extends CategoryTest {
  public static JiraClient jiraClient;

  @Before
  public void setup() {
    String url = "http://localhost:";
    JiraInternalConfig jiraInternalConfig = JiraInternalConfig.builder().jiraUrl(url).authToken("authToken").build();
    jiraClient = new JiraClient(jiraInternalConfig);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testConvertNewIssueTypeMetaData() {
    String projectKey = "TES";
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = new JiraIssueCreateMetadataNG();
    JiraIssueCreateMetadataNGIssueTypes jiraIssueCreateMetadataNGIssueTypes = new JiraIssueCreateMetadataNGIssueTypes();
    Map<String, JiraIssueTypeNG> issueTypeNGMap = new HashMap<>();
    JiraIssueTypeNG jiraIssueTypeNG1 = new JiraIssueTypeNG();
    jiraIssueTypeNG1.setSubTask(true);
    jiraIssueTypeNG1.setId("10000");
    jiraIssueTypeNG1.setName("Sub-task");
    jiraIssueTypeNG1.setDescription("The sub-task of the issue");
    JiraIssueTypeNG jiraIssueTypeNG2 = new JiraIssueTypeNG();
    jiraIssueTypeNG2.setSubTask(false);
    jiraIssueTypeNG2.setId("10001");
    jiraIssueTypeNG2.setName("Task");
    jiraIssueTypeNG2.setDescription("A task that needs to be done.");
    issueTypeNGMap.put("Task", jiraIssueTypeNG2);
    issueTypeNGMap.put("Sub-task", jiraIssueTypeNG1);
    jiraIssueCreateMetadataNGIssueTypes.setIssueTypes(issueTypeNGMap);
    jiraClient.originalMetadataFromNewIssueTypeMetadata(
        projectKey, jiraIssueCreateMetadataNG, jiraIssueCreateMetadataNGIssueTypes);
    assertThat(jiraIssueCreateMetadataNG.getProjects().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().size()).isEqualTo(2);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getId())
        .isEqualTo("10001");
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Sub-task").getId())
        .isEqualTo("10000");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testConvertNewFieldsMetaData() {
    String projectKey = "TES";
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = new JiraIssueCreateMetadataNG();
    JiraIssueCreateMetadataNGFields jiraIssueCreateMetadataNGFields = new JiraIssueCreateMetadataNGFields();
    JiraIssueTypeNG jiraIssueTypeNG = new JiraIssueTypeNG();
    jiraIssueTypeNG.setSubTask(false);
    jiraIssueTypeNG.setId("10001");
    jiraIssueTypeNG.setName("Task");
    jiraIssueTypeNG.setDescription("A task that needs to be done.");
    Map<String, JiraFieldNG> fieldNGMap = new HashMap<>();
    JiraFieldNG jiraFieldNG1 = new JiraFieldNG();
    jiraFieldNG1.setKey("assignee");
    jiraFieldNG1.setName("Assignee");
    jiraFieldNG1.setRequired(false);
    JiraFieldNG jiraFieldNG2 = new JiraFieldNG();
    jiraFieldNG2.setKey("duedate");
    jiraFieldNG2.setName("Due Date");
    jiraFieldNG2.setRequired(false);
    JiraFieldNG jiraFieldNG4 = new JiraFieldNG();
    jiraFieldNG4.setKey("description");
    jiraFieldNG4.setName("Description");
    jiraFieldNG4.setRequired(false);
    JiraFieldNG jiraFieldNG3 = new JiraFieldNG();
    jiraFieldNG3.setKey("summary");
    jiraFieldNG3.setName("Summary");
    jiraFieldNG3.setRequired(true);
    JiraFieldNG jiraFieldNG5 = new JiraFieldNG();
    jiraFieldNG5.setKey("labels");
    jiraFieldNG5.setName("Labels");
    jiraFieldNG5.setRequired(false);
    JiraFieldNG jiraFieldNG6 = new JiraFieldNG();
    jiraFieldNG6.setKey("priority");
    jiraFieldNG6.setName("Priority");
    jiraFieldNG6.setRequired(false);
    fieldNGMap.put("assignee", jiraFieldNG1);
    fieldNGMap.put("priority", jiraFieldNG6);
    fieldNGMap.put("labels", jiraFieldNG5);
    fieldNGMap.put("summary", jiraFieldNG3);
    fieldNGMap.put("description", jiraFieldNG4);
    fieldNGMap.put("duedate", jiraFieldNG2);
    jiraIssueCreateMetadataNGFields.setFields(fieldNGMap);
    jiraClient.originalMetadataFromNewFieldsMetadata(
        projectKey, jiraIssueCreateMetadataNG, jiraIssueTypeNG, jiraIssueCreateMetadataNGFields);
    assertThat(jiraIssueCreateMetadataNG.getProjects().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getId())
        .isEqualTo("10001");
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getFields().size())
        .isEqualTo(6);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getFields().get("Labels"))
        .isEqualTo(jiraFieldNG5);
  }
}
