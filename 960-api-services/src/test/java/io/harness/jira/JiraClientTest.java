/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.helpers.ext.servicenow.ServiceNowRestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class JiraClientTest extends CategoryTest {
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                          .usingFilesUnderDirectory("960-api-services/src/test/java")
                                                          .port(Options.DYNAMIC_PORT),
      false);
  public static JiraClient jiraClient;
  private static String url;
  private static final String ISSUE_KEY = "TJI-123";

  private static final String FILTER_FIELDS = "status,priority";

  @Mock private JiraRestClient jiraRestClient;
  @Mock private Call mockCall;
  private ClassLoader classLoader;

  @Before
  public void setup() {
    url = "http://localhost:" + wireMockRule.port();
    JiraInternalConfig jiraInternalConfig = JiraInternalConfig.builder().jiraUrl(url).authToken("authToken").build();
    jiraClient = new JiraClient(jiraInternalConfig);
    classLoader = this.getClass().getClassLoader();
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

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetIssueWithFilterFields() throws Exception {
    Map<String, StringValuePattern> queryParams = new HashMap<>();
    queryParams.put("expand", equalTo("names,schema"));
    queryParams.put("fields", equalTo(FILTER_FIELDS));
    wireMockRule.stubFor(get(urlPathEqualTo(String.format("/rest/api/2/issue/%s", ISSUE_KEY)))
                             .withQueryParams(queryParams)
                             .willReturn(aResponse().withStatus(200).withBody(
                                 getJsonStringFromJsonFile("jira/jiraIssueFilteredFields.json", classLoader))));

    //    Map<String, String> properties1 = new HashMap<>();
    //    properties1.put("self", "self");
    //    properties1.put("id", "ID");
    //    properties1.put("key", "key");
    //    properties1.put("url", "http://localhost:" + wireMockRule.port() + "/browse/key");
    //
    //    JsonNodeUtils.updatePropertiesInJsonNode(jsonissueNode, properties1);
    //    JiraIssueNG jiraIssueNG = new JiraIssueNG(jsonissueNode);
    //    jiraIssueNG.setUrl("http://localhost:" + wireMockRule.port() + "/browse/key");
    //    jiraIssueNG.getFields().put("url", "http://localhost:" + wireMockRule.port() + "/browse/key");
    //    JiraTaskNGResponse jiraTaskNGResponse = JiraTaskNGResponse.builder().issue(jiraIssueNG).build();
    JiraIssueNG jiraIssueNG = jiraClient.getIssue(ISSUE_KEY, FILTER_FIELDS);
  }

  private String getJsonStringFromJsonFile(String filePath, ClassLoader classLoader) throws Exception {
    URL jsonFile = this.getClass().getResource(filePath);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode responseNode = mapper.readTree(jsonFile);
    return responseNode.toString();
  }
}
