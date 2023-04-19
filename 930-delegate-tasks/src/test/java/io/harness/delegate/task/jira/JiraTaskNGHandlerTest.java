/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.MOUNIK;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.rule.OwnerRule.vivekveman;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraPATDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.delegate.task.jira.mappers.JiraRequestResponseMapper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JiraClientException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.JiraActionNG;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraFieldNG;
import io.harness.jira.JiraFieldSchemaNG;
import io.harness.jira.JiraFieldTypeNG;
import io.harness.jira.JiraInstanceData;
import io.harness.jira.JiraInstanceData.JiraDeploymentType;
import io.harness.jira.JiraInternalConfig;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraIssueTypeNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraProjectNG;
import io.harness.jira.JiraStatusNG;
import io.harness.jira.JiraUserData;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class JiraTaskNGHandlerTest extends CategoryTest {
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                          .usingFilesUnderDirectory("930-delegate-tasks/src/test/java")
                                                          .port(Options.DYNAMIC_PORT),
      false);
  private static String url;
  public static JiraClient jiraClient;
  private final JiraTaskNGHandler jiraTaskNGHandler = new JiraTaskNGHandler();
  private static final String MULTI = "multi";
  private static final String ISSUE_TYPE = "Issue Type";
  private static final String ISSUE_ID = "IssueId";
  private static final String CUSTOMFIELD_OPTION = "customfield_option";
  String JSON_RESOURCE = "testJson.json";
  ObjectNode jsonNode;
  ObjectNode jsonstatusNode;
  ObjectNode jsonissueNode;
  @Before
  public void setup() throws IOException {
    final String resource =
        IOUtils.resourceToString(JSON_RESOURCE, StandardCharsets.UTF_8, getClass().getClassLoader());
    ObjectMapper objectMapper = new ObjectMapper();
    jsonNode = (ObjectNode) objectMapper.readTree(resource);
    jsonstatusNode = (ObjectNode) objectMapper.readTree(resource);
    jsonissueNode = (ObjectNode) objectMapper.readTree(resource);
    Map<String, String> properties = new HashMap<>();
    properties.put("id", "ID");
    properties.put("key", "KEY");
    properties.put("name", "NAME");
    JsonNodeUtils.updatePropertiesInJsonNode(jsonNode, properties);
  }
  @Before
  public void before() {
    url = "http://localhost:" + wireMockRule.port();
    JiraInternalConfig jiraInternalConfig =
        JiraInternalConfig.builder().jiraUrl(url).authToken("dummyAccessToken").build();
    jiraClient = new JiraClient(jiraInternalConfig);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testValidateCredentials() throws Exception {
    try (MockedConstruction<JiraClient> ignored = mockConstruction(
             JiraClient.class, (mock, context) -> when(mock.getProjects()).thenReturn(Collections.emptyList()))) {
      assertThatCode(() -> jiraTaskNGHandler.validateCredentials(createJiraTaskParametersBuilder().build()))
          .doesNotThrowAnyException();
    }
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testValidateCredentialsError() throws Exception {
    try (MockedConstruction<JiraClient> ignored = mockConstruction(JiraClient.class,
             (mock, context) -> when(mock.getProjects()).thenThrow(new RuntimeException("exception")))) {
      assertThatThrownBy(() -> jiraTaskNGHandler.validateCredentials(createJiraTaskParametersBuilder().build()))
          .isNotNull();
    }
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testValidateCredentialsExactError() throws Exception {
    try (MockedConstruction<JiraClient> ignored = mockConstruction(JiraClient.class,
             (mock, context) -> when(mock.getProjects()).thenThrow(new RuntimeException("exception")))) {
      assertThatThrownBy(() -> jiraTaskNGHandler.validateCredentials(createJiraTaskParametersBuilder().build()))
          .isInstanceOf(HintException.class)
          .hasMessage(
              "Check if the Jira URL & Jira credentials are correct. Jira URLs are different for different credentials");
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetprojects() throws Exception {
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/2/project"))
                             .willReturn(aResponse().withStatus(200).withBody(
                                 "[{\"id\": \"ID\", \"name\": \"NAME\", \"key\": \"KEY\"}]")));
    JiraProjectBasicNG jiraProjectBasicNG = new JiraProjectBasicNG(jsonNode);
    List<JiraProjectBasicNG> projects = new ArrayList<>();
    projects.add(jiraProjectBasicNG);
    assertThat(jiraTaskNGHandler.getProjects(createJiraTaskParametersBuilder().build()))
        .isEqualTo(JiraTaskNGResponse.builder().projects(projects).build());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testgetprojectsWithPatAuth() throws Exception {
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/2/project"))
                             .willReturn(aResponse().withStatus(200).withBody(
                                 "[{\"id\": \"ID\", \"name\": \"NAME\", \"key\": \"KEY\"}]")));
    JiraProjectBasicNG jiraProjectBasicNG = new JiraProjectBasicNG(jsonNode);
    List<JiraProjectBasicNG> projects = new ArrayList<>();
    projects.add(jiraProjectBasicNG);
    JiraTaskNGParameters jiraTaskNGParameters = createPatAuthJiraTaskParametersBuilder().build();
    assertThat(jiraTaskNGHandler.getProjects(jiraTaskNGParameters))
        .isEqualTo(JiraTaskNGResponse.builder().projects(projects).build());
    JiraInternalConfig jiraInternalConfig =
        JiraRequestResponseMapper.toJiraInternalConfig(jiraTaskNGParameters.getJiraConnectorDTO());
    assertThat(jiraInternalConfig.getJiraUrl()).isEqualTo(url.concat("/"));
    assertThat(jiraInternalConfig.getAuthToken())
        .isEqualTo("Bearer %s",
            new String(((JiraPATDTO) jiraTaskNGParameters.getJiraConnectorDTO().getAuth().getCredentials())
                           .getPatRef()
                           .getDecryptedValue()));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetStatus() throws Exception {
    wireMockRule.stubFor(
        get(urlEqualTo("/rest/api/2/project/projectkey/statuses"))
            .willReturn(aResponse().withStatus(200).withBody(
                "[{\"id\": \"ID\", \"name\": \"NAME\" , \"description\": \"description\",\"subtask\": true,\"statuses\" :[{\"id\":\"ID\",\"name\" :\"NAME\"}]}]")));
    JiraProjectBasicNG jiraProjectBasicNG = new JiraProjectBasicNG(jsonNode);
    List<JiraProjectBasicNG> projects = new ArrayList<>();

    projects.add(jiraProjectBasicNG);

    Map<String, String> properties1 = new HashMap<>();
    properties1.put("id", "ID");
    properties1.put("name", "NAME");
    JsonNodeUtils.updatePropertiesInJsonNode(jsonstatusNode, properties1);

    List<JiraStatusNG> statuses = new ArrayList<>();
    statuses.add(new JiraStatusNG(jsonstatusNode));

    assertThat(jiraTaskNGHandler.getStatuses(
                   createJiraTaskParametersBuilder().projectKey("projectkey").issueType("NAME").build()))
        .isEqualTo(JiraTaskNGResponse.builder().statuses(statuses).build());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetIssue() throws Exception {
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/issue/issuekey"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"id\": \"ID\", \"name\": \"NAME\" , \"self\": \"self\",\"key\": \"key\",\"subtask\": true,\"statuses\" :[{\"id\":\"ID\",\"name\" :\"NAME\"}]}")));

    Map<String, String> properties1 = new HashMap<>();
    properties1.put("self", "self");
    properties1.put("id", "ID");
    properties1.put("key", "key");
    properties1.put("url", "http://localhost:" + wireMockRule.port() + "/browse/key");

    JsonNodeUtils.updatePropertiesInJsonNode(jsonissueNode, properties1);
    JiraIssueNG jiraIssueNG = new JiraIssueNG(jsonissueNode);
    jiraIssueNG.setUrl("http://localhost:" + wireMockRule.port() + "/browse/key");
    jiraIssueNG.getFields().put("url", "http://localhost:" + wireMockRule.port() + "/browse/key");
    JiraTaskNGResponse jiraTaskNGResponse = JiraTaskNGResponse.builder().issue(jiraIssueNG).build();
    assertThat(
        jiraTaskNGHandler.getIssue(
            createJiraTaskParametersBuilder().projectKey("projectkey").issueType("NAME").issueKey("issuekey").build()))
        .isEqualTo(jiraTaskNGResponse);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testgetIssueCreateMetadata() throws Exception {
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/serverInfo"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"baseUrl\":\"https://jira.dev.harness.io\",\"version\":\"8.5.3\",\"versionNumbers\":[8,5,3],\"deploymentType\":\"Server\",\"buildNumber\":805003,\"buildDate\":\"2020-01-09T00:00:00.000+0000\",\"databaseBuildNumber\":805003,\"serverTime\":\"2022-10-07T13:48:51.976+0000\",\"scmInfo\":\"b4933e02eaff29a49114274fe59e1f99d9d963d7\",\"serverTitle\":\"JIRA\"}")));
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/issue/createmeta/TES/issuetypes"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"maxResults\":50,\"startAt\":0,\"total\":2,\"isLast\":true,\"values\":[{\"self\":\"https://jira.dev.harness.io/rest/api/2/issuetype/10000\",\"id\":\"10000\",\"description\":\"Thesub-taskoftheissue\",\"iconUrl\":\"https://jira.dev.harness.io/images/icons/issuetypes/subtask_alternate.png\",\"name\":\"Sub-task\",\"subtask\":true},{\"self\":\"https://jira.dev.harness.io/rest/api/2/issuetype/10003\",\"id\":\"10003\",\"description\":\"Ataskthatneedstobedone.\",\"iconUrl\":\"https://jira.dev.harness.io/secure/viewavatar?size=xsmall&avatarId=10318&avatarType=issuetype\",\"name\":\"Task\",\"subtask\":false}]}")));

    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = jiraTaskNGHandler
                                                              .getIssueCreateMetadata(createJiraTaskParametersBuilder()
                                                                                          .projectKey("TES")
                                                                                          .fetchStatus(false)
                                                                                          .ignoreComment(false)
                                                                                          .expand(null)
                                                                                          .newMetadata(true)
                                                                                          .build())
                                                              .getIssueCreateMetadata();
    assertThat(jiraIssueCreateMetadataNG.getProjects().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().size()).isEqualTo(2);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getId())
        .isEqualTo("10003");
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Sub-task").getId())
        .isEqualTo("10000");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testgetIssueCreateMetadata2() throws Exception {
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/serverInfo"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"baseUrl\":\"https://jira.dev.harness.io\",\"version\":\"8.5.3\",\"versionNumbers\":[8,5,3],\"deploymentType\":\"Server\",\"buildNumber\":805003,\"buildDate\":\"2020-01-09T00:00:00.000+0000\",\"databaseBuildNumber\":805003,\"serverTime\":\"2022-10-07T13:48:51.976+0000\",\"scmInfo\":\"b4933e02eaff29a49114274fe59e1f99d9d963d7\",\"serverTitle\":\"JIRA\"}")));
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/issue/createmeta/TES/issuetypes"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"maxResults\":50,\"startAt\":0,\"total\":2,\"isLast\":true,\"values\":[{\"self\":\"https://jira.dev.harness.io/rest/api/2/issuetype/10000\",\"id\":\"10000\",\"description\":\"Thesub-taskoftheissue\",\"iconUrl\":\"https://jira.dev.harness.io/images/icons/issuetypes/subtask_alternate.png\",\"name\":\"Sub-task\",\"subtask\":true},{\"self\":\"https://jira.dev.harness.io/rest/api/2/issuetype/10003\",\"id\":\"10003\",\"description\":\"Ataskthatneedstobedone.\",\"iconUrl\":\"https://jira.dev.harness.io/secure/viewavatar?size=xsmall&avatarId=10318&avatarType=issuetype\",\"name\":\"Task\",\"subtask\":false}]}")));
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/issue/createmeta/TES/issuetypes/10003"))
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "    \"maxResults\": 50,\n"
                + "    \"startAt\": 0,\n"
                + "    \"total\": 12,\n"
                + "    \"isLast\": true,\n"
                + "    \"values\": [\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"user\",\n"
                + "                \"system\": \"assignee\"\n"
                + "            },\n"
                + "            \"name\": \"Assignee\",\n"
                + "            \"fieldId\": \"assignee\",\n"
                + "            \"autoCompleteUrl\": \"https://jira.dev.harness.io/rest/api/latest/user/assignable/search?issueKey=null&username=\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"array\",\n"
                + "                \"items\": \"option\",\n"
                + "                \"custom\": \"com.atlassian.jira.plugin.system.customfieldtypes:multiselect\",\n"
                + "                \"customId\": 10301\n"
                + "            },\n"
                + "            \"name\": \"custom_TID\",\n"
                + "            \"fieldId\": \"customfield_10301\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"add\",\n"
                + "                \"set\",\n"
                + "                \"remove\"\n"
                + "            ],\n"
                + "            \"allowedValues\": [\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10200\",\n"
                + "                    \"value\": \"OPTION1\",\n"
                + "                    \"id\": \"10200\",\n"
                + "                    \"disabled\": false\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10201\",\n"
                + "                    \"value\": \"OPTION2\",\n"
                + "                    \"id\": \"10201\",\n"
                + "                    \"disabled\": false\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10202\",\n"
                + "                    \"value\": \"OPTION3\",\n"
                + "                    \"id\": \"10202\",\n"
                + "                    \"disabled\": false\n"
                + "                }\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"array\",\n"
                + "                \"items\": \"option\",\n"
                + "                \"custom\": \"com.atlassian.jira.plugin.system.customfieldtypes:multicheckboxes\",\n"
                + "                \"customId\": 10302\n"
                + "            },\n"
                + "            \"name\": \"custom_CHECKBOX\",\n"
                + "            \"fieldId\": \"customfield_10302\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"add\",\n"
                + "                \"set\",\n"
                + "                \"remove\"\n"
                + "            ],\n"
                + "            \"allowedValues\": [\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10203\",\n"
                + "                    \"value\": \"OPT1\",\n"
                + "                    \"id\": \"10203\",\n"
                + "                    \"disabled\": false\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10204\",\n"
                + "                    \"value\": \"OPT2\",\n"
                + "                    \"id\": \"10204\",\n"
                + "                    \"disabled\": false\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10205\",\n"
                + "                    \"value\": \"OPT3\",\n"
                + "                    \"id\": \"10205\",\n"
                + "                    \"disabled\": false\n"
                + "                }\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"number\",\n"
                + "                \"custom\": \"com.atlassian.jira.plugin.system.customfieldtypes:float\",\n"
                + "                \"customId\": 10305\n"
                + "            },\n"
                + "            \"name\": \"customnumber\",\n"
                + "            \"fieldId\": \"customfield_10305\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"string\",\n"
                + "                \"system\": \"description\"\n"
                + "            },\n"
                + "            \"name\": \"Description\",\n"
                + "            \"fieldId\": \"description\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"date\",\n"
                + "                \"system\": \"duedate\"\n"
                + "            },\n"
                + "            \"name\": \"Due Date\",\n"
                + "            \"fieldId\": \"duedate\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": true,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"issuetype\",\n"
                + "                \"system\": \"issuetype\"\n"
                + "            },\n"
                + "            \"name\": \"Issue Type\",\n"
                + "            \"fieldId\": \"issuetype\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [],\n"
                + "            \"allowedValues\": [\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/issuetype/10003\",\n"
                + "                    \"id\": \"10003\",\n"
                + "                    \"description\": \"A task that needs to be done.\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/secure/viewavatar?size=xsmall&avatarId=10318&avatarType=issuetype\",\n"
                + "                    \"name\": \"Task\",\n"
                + "                    \"subtask\": false,\n"
                + "                    \"avatarId\": 10318\n"
                + "                }\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"array\",\n"
                + "                \"items\": \"string\",\n"
                + "                \"system\": \"labels\"\n"
                + "            },\n"
                + "            \"name\": \"Labels\",\n"
                + "            \"fieldId\": \"labels\",\n"
                + "            \"autoCompleteUrl\": \"https://jira.dev.harness.io/rest/api/1.0/labels/suggest?query=\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"add\",\n"
                + "                \"set\",\n"
                + "                \"remove\"\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": false,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"priority\",\n"
                + "                \"system\": \"priority\"\n"
                + "            },\n"
                + "            \"name\": \"Priority\",\n"
                + "            \"fieldId\": \"priority\",\n"
                + "            \"hasDefaultValue\": true,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ],\n"
                + "            \"allowedValues\": [\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/1\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/highest.svg\",\n"
                + "                    \"name\": \"Highest\",\n"
                + "                    \"id\": \"1\"\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/2\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/high.svg\",\n"
                + "                    \"name\": \"High\",\n"
                + "                    \"id\": \"2\"\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/3\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/medium.svg\",\n"
                + "                    \"name\": \"Medium\",\n"
                + "                    \"id\": \"3\"\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/4\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/low.svg\",\n"
                + "                    \"name\": \"Low\",\n"
                + "                    \"id\": \"4\"\n"
                + "                },\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/5\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/lowest.svg\",\n"
                + "                    \"name\": \"Lowest\",\n"
                + "                    \"id\": \"5\"\n"
                + "                }\n"
                + "            ],\n"
                + "            \"defaultValue\": {\n"
                + "                \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/3\",\n"
                + "                \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/medium.svg\",\n"
                + "                \"name\": \"Medium\",\n"
                + "                \"id\": \"3\"\n"
                + "            }\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": true,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"project\",\n"
                + "                \"system\": \"project\"\n"
                + "            },\n"
                + "            \"name\": \"Project\",\n"
                + "            \"fieldId\": \"project\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ],\n"
                + "            \"allowedValues\": [\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/project/10101\",\n"
                + "                    \"id\": \"10101\",\n"
                + "                    \"key\": \"TES\",\n"
                + "                    \"name\": \"TestTask\",\n"
                + "                    \"projectTypeKey\": \"business\",\n"
                + "                    \"avatarUrls\": {\n"
                + "                        \"48x48\": \"https://jira.dev.harness.io/secure/projectavatar?avatarId=10324\",\n"
                + "                        \"24x24\": \"https://jira.dev.harness.io/secure/projectavatar?size=small&avatarId=10324\",\n"
                + "                        \"16x16\": \"https://jira.dev.harness.io/secure/projectavatar?size=xsmall&avatarId=10324\",\n"
                + "                        \"32x32\": \"https://jira.dev.harness.io/secure/projectavatar?size=medium&avatarId=10324\"\n"
                + "                    }\n"
                + "                }\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": true,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"user\",\n"
                + "                \"system\": \"reporter\"\n"
                + "            },\n"
                + "            \"name\": \"Reporter\",\n"
                + "            \"fieldId\": \"reporter\",\n"
                + "            \"autoCompleteUrl\": \"https://jira.dev.harness.io/rest/api/latest/user/search?username=\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"required\": true,\n"
                + "            \"schema\": {\n"
                + "                \"type\": \"string\",\n"
                + "                \"system\": \"summary\"\n"
                + "            },\n"
                + "            \"name\": \"Summary\",\n"
                + "            \"fieldId\": \"summary\",\n"
                + "            \"hasDefaultValue\": false,\n"
                + "            \"operations\": [\n"
                + "                \"set\"\n"
                + "            ]\n"
                + "        }\n"
                + "    ]\n"
                + "}")));

    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = jiraTaskNGHandler
                                                              .getIssueCreateMetadata(createJiraTaskParametersBuilder()
                                                                                          .projectKey("TES")
                                                                                          .issueType("Task")
                                                                                          .fetchStatus(false)
                                                                                          .ignoreComment(false)
                                                                                          .expand(null)
                                                                                          .newMetadata(true)
                                                                                          .build())
                                                              .getIssueCreateMetadata();
    assertThat(jiraIssueCreateMetadataNG.getProjects().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getId())
        .isEqualTo("10003");
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getFields().size())
        .isEqualTo(10);
    // createMetadata should not contain issue type field
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getFields().containsKey(
                   "Issue Type"))
        .isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testgetIssueCreateMetadata3() throws Exception {
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/serverInfo"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"baseUrl\":\"https://jira.dev.harness.io\",\"version\":\"8.5.3\",\"versionNumbers\":[8,5,3],\"deploymentType\":\"Server\",\"buildNumber\":805003,\"buildDate\":\"2020-01-09T00:00:00.000+0000\",\"databaseBuildNumber\":805003,\"serverTime\":\"2022-10-07T13:48:51.976+0000\",\"scmInfo\":\"b4933e02eaff29a49114274fe59e1f99d9d963d7\",\"serverTitle\":\"JIRA\"}")));
    Map<String, StringValuePattern> map = new HashMap<>();
    map.put("projectKeys", equalTo("TES"));
    map.put("issuetypeNames", equalTo("Task"));
    map.put("expand", equalTo("projects.issuetypes.fields"));
    wireMockRule.stubFor(
        get(urlPathEqualTo("/rest/api/2/issue/createmeta"))
            .withQueryParams(map)
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "    \"expand\": \"projects\",\n"
                + "    \"projects\": [\n"
                + "        {\n"
                + "            \"expand\": \"issuetypes\",\n"
                + "            \"self\": \"https://jira.dev.harness.io/rest/api/2/project/10101\",\n"
                + "            \"id\": \"10101\",\n"
                + "            \"key\": \"TES\",\n"
                + "            \"name\": \"TestTask\",\n"
                + "            \"avatarUrls\": {\n"
                + "                \"48x48\": \"https://jira.dev.harness.io/secure/projectavatar?avatarId=10324\",\n"
                + "                \"24x24\": \"https://jira.dev.harness.io/secure/projectavatar?size=small&avatarId=10324\",\n"
                + "                \"16x16\": \"https://jira.dev.harness.io/secure/projectavatar?size=xsmall&avatarId=10324\",\n"
                + "                \"32x32\": \"https://jira.dev.harness.io/secure/projectavatar?size=medium&avatarId=10324\"\n"
                + "            },\n"
                + "            \"issuetypes\": [\n"
                + "                {\n"
                + "                    \"self\": \"https://jira.dev.harness.io/rest/api/2/issuetype/10003\",\n"
                + "                    \"id\": \"10003\",\n"
                + "                    \"description\": \"A task that needs to be done.\",\n"
                + "                    \"iconUrl\": \"https://jira.dev.harness.io/secure/viewavatar?size=xsmall&avatarId=10318&avatarType=issuetype\",\n"
                + "                    \"name\": \"Task\",\n"
                + "                    \"subtask\": false,\n"
                + "                    \"expand\": \"fields\",\n"
                + "                    \"fields\": {\n"
                + "                        \"summary\": {\n"
                + "                            \"required\": true,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"string\",\n"
                + "                                \"system\": \"summary\"\n"
                + "                            },\n"
                + "                            \"name\": \"Summary\",\n"
                + "                            \"fieldId\": \"summary\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"issuetype\": {\n"
                + "                            \"required\": true,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"issuetype\",\n"
                + "                                \"system\": \"issuetype\"\n"
                + "                            },\n"
                + "                            \"name\": \"Issue Type\",\n"
                + "                            \"fieldId\": \"issuetype\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [],\n"
                + "                            \"allowedValues\": [\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/issuetype/10003\",\n"
                + "                                    \"id\": \"10003\",\n"
                + "                                    \"description\": \"A task that needs to be done.\",\n"
                + "                                    \"iconUrl\": \"https://jira.dev.harness.io/secure/viewavatar?size=xsmall&avatarId=10318&avatarType=issuetype\",\n"
                + "                                    \"name\": \"Task\",\n"
                + "                                    \"subtask\": false,\n"
                + "                                    \"avatarId\": 10318\n"
                + "                                }\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"reporter\": {\n"
                + "                            \"required\": true,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"user\",\n"
                + "                                \"system\": \"reporter\"\n"
                + "                            },\n"
                + "                            \"name\": \"Reporter\",\n"
                + "                            \"fieldId\": \"reporter\",\n"
                + "                            \"autoCompleteUrl\": \"https://jira.dev.harness.io/rest/api/latest/user/search?username=\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"duedate\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"date\",\n"
                + "                                \"system\": \"duedate\"\n"
                + "                            },\n"
                + "                            \"name\": \"Due Date\",\n"
                + "                            \"fieldId\": \"duedate\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"description\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"string\",\n"
                + "                                \"system\": \"description\"\n"
                + "                            },\n"
                + "                            \"name\": \"Description\",\n"
                + "                            \"fieldId\": \"description\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"priority\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"priority\",\n"
                + "                                \"system\": \"priority\"\n"
                + "                            },\n"
                + "                            \"name\": \"Priority\",\n"
                + "                            \"fieldId\": \"priority\",\n"
                + "                            \"hasDefaultValue\": true,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ],\n"
                + "                            \"allowedValues\": [\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/1\",\n"
                + "                                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/highest.svg\",\n"
                + "                                    \"name\": \"Highest\",\n"
                + "                                    \"id\": \"1\"\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/2\",\n"
                + "                                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/high.svg\",\n"
                + "                                    \"name\": \"High\",\n"
                + "                                    \"id\": \"2\"\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/3\",\n"
                + "                                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/medium.svg\",\n"
                + "                                    \"name\": \"Medium\",\n"
                + "                                    \"id\": \"3\"\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/4\",\n"
                + "                                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/low.svg\",\n"
                + "                                    \"name\": \"Low\",\n"
                + "                                    \"id\": \"4\"\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/5\",\n"
                + "                                    \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/lowest.svg\",\n"
                + "                                    \"name\": \"Lowest\",\n"
                + "                                    \"id\": \"5\"\n"
                + "                                }\n"
                + "                            ],\n"
                + "                            \"defaultValue\": {\n"
                + "                                \"self\": \"https://jira.dev.harness.io/rest/api/2/priority/3\",\n"
                + "                                \"iconUrl\": \"https://jira.dev.harness.io/images/icons/priorities/medium.svg\",\n"
                + "                                \"name\": \"Medium\",\n"
                + "                                \"id\": \"3\"\n"
                + "                            }\n"
                + "                        },\n"
                + "                        \"labels\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"array\",\n"
                + "                                \"items\": \"string\",\n"
                + "                                \"system\": \"labels\"\n"
                + "                            },\n"
                + "                            \"name\": \"Labels\",\n"
                + "                            \"fieldId\": \"labels\",\n"
                + "                            \"autoCompleteUrl\": \"https://jira.dev.harness.io/rest/api/1.0/labels/suggest?query=\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"add\",\n"
                + "                                \"set\",\n"
                + "                                \"remove\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"customfield_10301\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"array\",\n"
                + "                                \"items\": \"option\",\n"
                + "                                \"custom\": \"com.atlassian.jira.plugin.system.customfieldtypes:multiselect\",\n"
                + "                                \"customId\": 10301\n"
                + "                            },\n"
                + "                            \"name\": \"custom_TID\",\n"
                + "                            \"fieldId\": \"customfield_10301\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"add\",\n"
                + "                                \"set\",\n"
                + "                                \"remove\"\n"
                + "                            ],\n"
                + "                            \"allowedValues\": [\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10200\",\n"
                + "                                    \"value\": \"OPTION1\",\n"
                + "                                    \"id\": \"10200\",\n"
                + "                                    \"disabled\": false\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10201\",\n"
                + "                                    \"value\": \"OPTION2\",\n"
                + "                                    \"id\": \"10201\",\n"
                + "                                    \"disabled\": false\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10202\",\n"
                + "                                    \"value\": \"OPTION3\",\n"
                + "                                    \"id\": \"10202\",\n"
                + "                                    \"disabled\": false\n"
                + "                                }\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"customfield_10302\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"array\",\n"
                + "                                \"items\": \"option\",\n"
                + "                                \"custom\": \"com.atlassian.jira.plugin.system.customfieldtypes:multicheckboxes\",\n"
                + "                                \"customId\": 10302\n"
                + "                            },\n"
                + "                            \"name\": \"custom_CHECKBOX\",\n"
                + "                            \"fieldId\": \"customfield_10302\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"add\",\n"
                + "                                \"set\",\n"
                + "                                \"remove\"\n"
                + "                            ],\n"
                + "                            \"allowedValues\": [\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10203\",\n"
                + "                                    \"value\": \"OPT1\",\n"
                + "                                    \"id\": \"10203\",\n"
                + "                                    \"disabled\": false\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10204\",\n"
                + "                                    \"value\": \"OPT2\",\n"
                + "                                    \"id\": \"10204\",\n"
                + "                                    \"disabled\": false\n"
                + "                                },\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/customFieldOption/10205\",\n"
                + "                                    \"value\": \"OPT3\",\n"
                + "                                    \"id\": \"10205\",\n"
                + "                                    \"disabled\": false\n"
                + "                                }\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"customfield_10305\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"number\",\n"
                + "                                \"custom\": \"com.atlassian.jira.plugin.system.customfieldtypes:float\",\n"
                + "                                \"customId\": 10305\n"
                + "                            },\n"
                + "                            \"name\": \"customnumber\",\n"
                + "                            \"fieldId\": \"customfield_10305\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"assignee\": {\n"
                + "                            \"required\": false,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"user\",\n"
                + "                                \"system\": \"assignee\"\n"
                + "                            },\n"
                + "                            \"name\": \"Assignee\",\n"
                + "                            \"fieldId\": \"assignee\",\n"
                + "                            \"autoCompleteUrl\": \"https://jira.dev.harness.io/rest/api/latest/user/assignable/search?issueKey=null&username=\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ]\n"
                + "                        },\n"
                + "                        \"project\": {\n"
                + "                            \"required\": true,\n"
                + "                            \"schema\": {\n"
                + "                                \"type\": \"project\",\n"
                + "                                \"system\": \"project\"\n"
                + "                            },\n"
                + "                            \"name\": \"Project\",\n"
                + "                            \"fieldId\": \"project\",\n"
                + "                            \"hasDefaultValue\": false,\n"
                + "                            \"operations\": [\n"
                + "                                \"set\"\n"
                + "                            ],\n"
                + "                            \"allowedValues\": [\n"
                + "                                {\n"
                + "                                    \"self\": \"https://jira.dev.harness.io/rest/api/2/project/10101\",\n"
                + "                                    \"id\": \"10101\",\n"
                + "                                    \"key\": \"TES\",\n"
                + "                                    \"name\": \"TestTask\",\n"
                + "                                    \"projectTypeKey\": \"business\",\n"
                + "                                    \"avatarUrls\": {\n"
                + "                                        \"48x48\": \"https://jira.dev.harness.io/secure/projectavatar?avatarId=10324\",\n"
                + "                                        \"24x24\": \"https://jira.dev.harness.io/secure/projectavatar?size=small&avatarId=10324\",\n"
                + "                                        \"16x16\": \"https://jira.dev.harness.io/secure/projectavatar?size=xsmall&avatarId=10324\",\n"
                + "                                        \"32x32\": \"https://jira.dev.harness.io/secure/projectavatar?size=medium&avatarId=10324\"\n"
                + "                                    }\n"
                + "                                }\n"
                + "                            ]\n"
                + "                        }\n"
                + "                    }\n"
                + "                }\n"
                + "            ]\n"
                + "        }\n"
                + "    ]\n"
                + "}")));
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = jiraTaskNGHandler
                                                              .getIssueCreateMetadata(createJiraTaskParametersBuilder()
                                                                                          .projectKey("TES")
                                                                                          .issueType("Task")
                                                                                          .fetchStatus(false)
                                                                                          .ignoreComment(false)
                                                                                          .expand(null)
                                                                                          .newMetadata(false)
                                                                                          .build())
                                                              .getIssueCreateMetadata();
    assertThat(jiraIssueCreateMetadataNG.getProjects().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().size()).isEqualTo(1);
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getId()).isEqualTo("10101");
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getId())
        .isEqualTo("10003");
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getFields().size())
        .isEqualTo(11);
    // createMetadata should not contain issue type field
    assertThat(jiraIssueCreateMetadataNG.getProjects().get("TES").getIssueTypes().get("Task").getFields().containsKey(
                   "Issue Type"))
        .isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testcreateIssue() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");
    JiraTaskNGParameters jiraTaskNGParameters = createJiraTaskParametersBuilder()
                                                    .action(JiraActionNG.CREATE_ISSUE)
                                                    .projectKey("TJI")
                                                    .issueType("Bug")
                                                    .fields(fields)
                                                    .fetchStatus(false)
                                                    .ignoreComment(false)
                                                    .build();
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = Mockito.mock(JiraIssueCreateMetadataNG.class);

    JiraProjectNG jiraProjectNG = Mockito.mock(JiraProjectNG.class);
    Map<String, JiraProjectNG> project = new HashMap<>();
    project.put("TJI", jiraProjectNG);
    when(jiraIssueCreateMetadataNG.getProjects()).thenReturn(project);

    JiraIssueTypeNG jiraIssueTypeNG = Mockito.mock(JiraIssueTypeNG.class);
    Map<String, JiraIssueTypeNG> issueType = new HashMap<>();
    issueType.put("Bug", jiraIssueTypeNG);
    when(jiraProjectNG.getIssueTypes()).thenReturn(issueType);

    Map<String, JiraFieldNG> fieldsMap = new HashMap<>();
    JiraFieldNG jiraFieldNG1 = JiraFieldNG.builder().build();
    jiraFieldNG1.setKey("QE Assignee");
    jiraFieldNG1.setName("field1");
    jiraFieldNG1.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.USER).build());

    JiraFieldNG jiraFieldNG2 = JiraFieldNG.builder().build();
    jiraFieldNG2.setKey("Test Summary");
    jiraFieldNG2.setName("field2");
    jiraFieldNG2.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build());

    fieldsMap.put("QE Assignee", jiraFieldNG1);
    fieldsMap.put("Test Summary", jiraFieldNG2);
    when(jiraIssueTypeNG.getFields()).thenReturn(fieldsMap);

    JiraUserData jiraUserData = new JiraUserData("accountId", "assignee", true, "your-jira-account-id");

    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "accountId");
    fields1.put("Test Summary", "No test added");
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD);
    try (MockedConstruction<JiraClient> ignored = mockConstruction(JiraClient.class, (mock, context) -> {
      when(mock.getIssueCreateMetadata("TJI", "Bug", null, false, false, false, false))
          .thenReturn(jiraIssueCreateMetadataNG);
      when(mock.getUsers("your-jira-account-id", null, null)).thenReturn(Arrays.asList(jiraUserData));
      when(mock.createIssue("TJI", "Bug", fields1, true, false, false)).thenReturn(jiraIssueNG);
      when(mock.getInstanceData()).thenReturn(jiraInstanceData);
    })) {
      JiraTaskNGResponse jiraTaskNGResponse = jiraTaskNGHandler.createIssue(jiraTaskNGParameters);

      assertThat(jiraTaskNGResponse.getIssue()).isNotNull();
      assertThat(jiraTaskNGResponse).isNotNull();
      assertThat(jiraTaskNGResponse.getIssue()).isEqualTo(jiraIssueNG);
      assertThat(jiraTaskNGParameters.getFields()).isEqualTo(fields1);
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testcreateIssue2() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");

    JiraTaskNGParameters jiraTaskNGParameters = createJiraTaskParametersBuilder()
                                                    .action(JiraActionNG.CREATE_ISSUE)
                                                    .projectKey("TJI")
                                                    .issueType("Bug")
                                                    .fields(fields)
                                                    .fetchStatus(false)
                                                    .ignoreComment(false)
                                                    .build();
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = Mockito.mock(JiraIssueCreateMetadataNG.class);

    JiraProjectNG jiraProjectNG = Mockito.mock(JiraProjectNG.class);
    Map<String, JiraProjectNG> project = new HashMap<>();
    project.put("TJI", jiraProjectNG);
    when(jiraIssueCreateMetadataNG.getProjects()).thenReturn(project);

    JiraIssueTypeNG jiraIssueTypeNG = Mockito.mock(JiraIssueTypeNG.class);
    Map<String, JiraIssueTypeNG> issueType = new HashMap<>();
    issueType.put("Bug", jiraIssueTypeNG);
    when(jiraProjectNG.getIssueTypes()).thenReturn(issueType);

    Map<String, JiraFieldNG> fieldsMap = new HashMap<>();
    JiraFieldNG jiraFieldNG1 = JiraFieldNG.builder().build();
    jiraFieldNG1.setKey("QE Assignee");
    jiraFieldNG1.setName("field1");
    jiraFieldNG1.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.USER).build());

    JiraFieldNG jiraFieldNG2 = JiraFieldNG.builder().build();
    jiraFieldNG2.setKey("Test Summary");
    jiraFieldNG2.setName("field2");
    jiraFieldNG2.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build());

    fieldsMap.put("QE Assignee", jiraFieldNG1);
    fieldsMap.put("Test Summary", jiraFieldNG2);
    when(jiraIssueTypeNG.getFields()).thenReturn(fieldsMap);

    JiraUserData jiraUserData = new JiraUserData("JIRAUSERaccountId", "assignee", true, "your-jira-account-id");
    jiraUserData.setName("Assignee");

    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "Assignee");
    fields1.put("Test Summary", "No test added");
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD);
    try (MockedConstruction<JiraClient> ignored = mockConstruction(JiraClient.class, (mock, context) -> {
      when(mock.getIssueCreateMetadata("TJI", "Bug", null, false, false, false, false))
          .thenReturn(jiraIssueCreateMetadataNG);
      when(mock.getUsers("your-jira-account-id", null, null)).thenReturn(Arrays.asList(jiraUserData));
      when(mock.createIssue("TJI", "Bug", fields1, true, false, false)).thenReturn(jiraIssueNG);
      when(mock.getInstanceData()).thenReturn(jiraInstanceData);
    })) {
      JiraTaskNGResponse jiraTaskNGResponse = jiraTaskNGHandler.createIssue(jiraTaskNGParameters);

      assertThat(jiraTaskNGResponse.getIssue()).isNotNull();
      assertThat(jiraTaskNGResponse).isNotNull();
      assertThat(jiraTaskNGResponse.getIssue()).isEqualTo(jiraIssueNG);
      assertThat(jiraTaskNGParameters.getFields()).isEqualTo(fields1);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCreateIssueWhenCreateMetadataThrowsException() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("Test Summary", "No test added");
    JiraTaskNGParameters jiraTaskNGParameters = createJiraTaskParametersBuilder()
                                                    .action(JiraActionNG.CREATE_ISSUE)
                                                    .projectKey("Invalid-Project-Key")
                                                    .issueType("Bug")
                                                    .fields(fields)
                                                    .fetchStatus(false)
                                                    .ignoreComment(false)
                                                    .build();

    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);

    when(jiraClient.getIssueCreateMetadata("Invalid-Project-Key", "Bug", null, false, false, false, false))
        .thenThrow(new JiraClientException("invalid project key"));

    when(jiraClient.createIssue("Invalid-Project-Key", "Bug", fields, true, false, false))
        .thenThrow(new HintException("dummy hint"));

    assertThatThrownBy(() -> jiraTaskNGHandler.createIssue(jiraTaskNGParameters)).isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testupdateIssue() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");
    fields.put("Issue Type", "Change");

    JiraTaskNGParameters jiraTaskNGParameters = createJiraTaskParametersBuilder()
                                                    .action(JiraActionNG.UPDATE_ISSUE)
                                                    .projectKey("TJI")
                                                    .issueKey("TJI-37792")
                                                    .transitionToStatus("INVALID")
                                                    .fields(fields)
                                                    .fetchStatus(false)
                                                    .ignoreComment(false)
                                                    .build();

    JiraIssueUpdateMetadataNG jiraIssueUpdateMetadataNG = Mockito.mock(JiraIssueUpdateMetadataNG.class);

    Map<String, JiraFieldNG> fieldsMap = new HashMap<>();
    JiraFieldNG jiraFieldNG1 = JiraFieldNG.builder().build();
    jiraFieldNG1.setKey("QE Assignee");
    jiraFieldNG1.setName("field1");
    jiraFieldNG1.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.USER).build());

    JiraFieldNG jiraFieldNG2 = JiraFieldNG.builder().build();
    jiraFieldNG2.setKey("Test Summary");
    jiraFieldNG2.setName("field2");
    jiraFieldNG2.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build());

    JiraFieldNG jiraFieldNG3 = JiraFieldNG.builder().build();
    jiraFieldNG3.setKey("issuetype");
    jiraFieldNG3.setName("Issue Type");
    jiraFieldNG3.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.ISSUE_TYPE).build());

    fieldsMap.put("QE Assignee", jiraFieldNG1);
    fieldsMap.put("Test Summary", jiraFieldNG2);
    fieldsMap.put("Issue Type", jiraFieldNG3);
    when(jiraIssueUpdateMetadataNG.getFields()).thenReturn(fieldsMap);

    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraDeploymentType.CLOUD);
    JiraUserData jiraUserData = new JiraUserData("accountId", "assignee", true, "your-jira-account-id");
    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "accountId");
    fields1.put("Test Summary", "No test added");
    fields1.put("Issue Type", "Change");
    try (MockedConstruction<JiraClient> ignored = mockConstruction(JiraClient.class, (mock, context) -> {
      when(mock.getIssueUpdateMetadata("TJI-37792")).thenReturn(jiraIssueUpdateMetadataNG);
      when(mock.getUsers("your-jira-account-id", null, null)).thenReturn(Arrays.asList(jiraUserData));
      when(mock.updateIssue(
               jiraTaskNGParameters.getIssueKey(), jiraTaskNGParameters.getTransitionToStatus(), null, fields1))
          .thenReturn(jiraIssueNG);
      when(mock.getInstanceData()).thenReturn(jiraInstanceData);
    })) {
      JiraTaskNGResponse jiraTaskNGResponse = jiraTaskNGHandler.updateIssue(jiraTaskNGParameters);

      assertThat(jiraTaskNGResponse.getIssue()).isNotNull();
      assertThat(jiraTaskNGResponse).isNotNull();
      assertThat(jiraTaskNGResponse.getIssue()).isEqualTo(jiraIssueNG);
      assertThat(jiraTaskNGParameters.getFields()).isEqualTo(fields1);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateIssueWhenUpdateMetadataThrowsException() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("Test Summary", "No test added");

    JiraTaskNGParameters jiraTaskNGParameters = createJiraTaskParametersBuilder()
                                                    .action(JiraActionNG.UPDATE_ISSUE)
                                                    .projectKey("TJI")
                                                    .issueKey("TJI-37792-invalid")
                                                    .fields(fields)
                                                    .fetchStatus(false)
                                                    .ignoreComment(false)
                                                    .build();

    JiraClient jiraClient = Mockito.mock(JiraClient.class);

    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);
    when(jiraClient.getIssueUpdateMetadata("TJI-37792-invalid")).thenThrow(new JiraClientException("dummy"));

    when(jiraClient.updateIssue(
             jiraTaskNGParameters.getIssueKey(), jiraTaskNGParameters.getTransitionToStatus(), null, fields))
        .thenThrow(new JiraClientException("invalid issue key"));

    assertThatThrownBy(() -> jiraTaskNGHandler.updateIssue(jiraTaskNGParameters))
        .isInstanceOf(JiraClientException.class);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testIssueForException() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");

    JiraTaskNGParameters jiraTaskNGParameters = createJiraTaskParametersBuilder()
                                                    .action(JiraActionNG.CREATE_ISSUE)
                                                    .projectKey("TJI")
                                                    .issueType("Bug")
                                                    .issueKey("TJI-37792")
                                                    .transitionToStatus("INVALID")
                                                    .fields(fields)
                                                    .fetchStatus(false)
                                                    .ignoreComment(false)
                                                    .build();

    JiraIssueUpdateMetadataNG jiraIssueUpdateMetadataNG = Mockito.mock(JiraIssueUpdateMetadataNG.class);

    Map<String, JiraFieldNG> fieldsMap = new HashMap<>();
    JiraFieldNG jiraFieldNG1 = JiraFieldNG.builder().build();
    jiraFieldNG1.setKey("QE Assignee");
    jiraFieldNG1.setName("field1");
    jiraFieldNG1.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.USER).build());

    JiraFieldNG jiraFieldNG2 = JiraFieldNG.builder().build();
    jiraFieldNG2.setKey("Test Summary");
    jiraFieldNG2.setName("field2");
    jiraFieldNG2.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build());

    fieldsMap.put("QE Assignee", jiraFieldNG1);
    fieldsMap.put("Test Summary", jiraFieldNG2);
    when(jiraIssueUpdateMetadataNG.getFields()).thenReturn(fieldsMap);

    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraDeploymentType.CLOUD);

    JiraUserData jiraUserData1 = new JiraUserData("accountId1", "assignee1", true, "your-jira-account-id-1");
    JiraUserData jiraUserData2 = new JiraUserData("accountId2", "assignee2", true, "your-jira-account-id-2");
    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "accountId");
    fields1.put("Test Summary", "No test added");
    try (MockedConstruction<JiraClient> ignored = mockConstruction(JiraClient.class, (mock, context) -> {
      when(mock.getIssueUpdateMetadata("TJI-37792")).thenReturn(jiraIssueUpdateMetadataNG);
      when(mock.getUsers("your-jira-account-id", null, null)).thenReturn(Arrays.asList(jiraUserData1, jiraUserData2));
      when(mock.updateIssue(
               jiraTaskNGParameters.getIssueKey(), jiraTaskNGParameters.getTransitionToStatus(), null, fields1))
          .thenReturn(jiraIssueNG);
      when(mock.getInstanceData()).thenReturn(jiraInstanceData);
    })) {
      assertThatExceptionOfType(InvalidRequestException.class)
          .isThrownBy(() -> jiraTaskNGHandler.updateIssue(jiraTaskNGParameters));
    }
  }

  private JiraTaskNGParametersBuilder createJiraTaskParametersBuilder() {
    JiraConnectorDTO jiraConnectorDTO =
        JiraConnectorDTO.builder()
            .jiraUrl(url)
            .auth(JiraAuthenticationDTO.builder()
                      .authType(JiraAuthType.USER_PASSWORD)
                      .credentials(
                          JiraUserNamePasswordDTO.builder()
                              .username("username")
                              .passwordRef(
                                  SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
                              .build())
                      .build())
            .build();
    return JiraTaskNGParameters.builder().jiraConnectorDTO(jiraConnectorDTO);
  }

  private JiraTaskNGParametersBuilder createPatAuthJiraTaskParametersBuilder() {
    JiraConnectorDTO jiraConnectorDTO =
        JiraConnectorDTO.builder()
            .jiraUrl(url)
            .auth(JiraAuthenticationDTO.builder()
                      .authType(JiraAuthType.PAT)
                      .credentials(
                          JiraPATDTO.builder()
                              .patRef(
                                  SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
                              .build())
                      .build())
            .build();
    return JiraTaskNGParameters.builder().jiraConnectorDTO(jiraConnectorDTO);
  }
}