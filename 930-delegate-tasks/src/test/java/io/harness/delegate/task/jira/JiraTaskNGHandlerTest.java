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
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.rule.OwnerRule.vivekveman;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.JiraActionNG;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraFieldNG;
import io.harness.jira.JiraFieldSchemaNG;
import io.harness.jira.JiraFieldTypeNG;
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
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(CDC)
@RunWith(PowerMockRunner.class)
@PrepareForTest({JiraClient.class, JiraTaskNGHandler.class})
@PowerMockIgnore({"javax.net.ssl.*"})
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
        JiraInternalConfig.builder().jiraUrl(url).username("username").password("password").build();
    jiraClient = new JiraClient(jiraInternalConfig);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testValidateCredentials() throws Exception {
    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    when(jiraClient.getProjects()).thenReturn(Collections.emptyList());
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);

    assertThatCode(() -> jiraTaskNGHandler.validateCredentials(createJiraTaskParametersBuilder().build()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testValidateCredentialsError() throws Exception {
    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    when(jiraClient.getProjects()).thenThrow(new RuntimeException("exception"));
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);

    assertThatThrownBy(() -> jiraTaskNGHandler.validateCredentials(createJiraTaskParametersBuilder().build()))
        .isNotNull();
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testValidateCredentialsExactError() throws Exception {
    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    when(jiraClient.getProjects()).thenThrow(new RuntimeException("exception"));
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);

    assertThatThrownBy(() -> jiraTaskNGHandler.validateCredentials(createJiraTaskParametersBuilder().build()))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Check if the Jira URL & Jira credentials are correct. Jira URLs are different for different credentials");
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
    assertThat(jiraTaskNGHandler.getProjects(
                   createJiraTaskParametersBuilder()
                       .jiraConnectorDTO(
                           JiraConnectorDTO.builder()
                               .jiraUrl(url)
                               .username("username")
                               .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
                               .build())
                       .build()))
        .isEqualTo(JiraTaskNGResponse.builder().projects(projects).build());
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
                   createJiraTaskParametersBuilder()
                       .jiraConnectorDTO(JiraConnectorDTO.builder()
                                             .jiraUrl(url)
                                             .username("username")
                                             .passwordRef(SecretRefData.builder()
                                                              .decryptedValue("password".toCharArray())

                                                              .build())
                                             .build())
                       .projectKey("projectkey")
                       .issueType("NAME")
                       .build()))
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
        jiraTaskNGHandler.getIssue(createJiraTaskParametersBuilder()
                                       .jiraConnectorDTO(JiraConnectorDTO.builder()
                                                             .jiraUrl(url)
                                                             .username("username")
                                                             .passwordRef(SecretRefData.builder()
                                                                              .decryptedValue("password".toCharArray())

                                                                              .build())
                                                             .build())
                                       .projectKey("projectkey")
                                       .issueType("NAME")
                                       .issueKey("issuekey")
                                       .build()))
        .isEqualTo(jiraTaskNGResponse);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testcreateIssue() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");
    JiraConnectorDTO jiraConnectorDTO =
        JiraConnectorDTO.builder()
            .jiraUrl("https://harness.atlassian.net/")
            .username("username")
            .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
            .build();
    JiraTaskNGParameters jiraTaskNGParameters = JiraTaskNGParameters.builder()
                                                    .jiraConnectorDTO(jiraConnectorDTO)
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

    JiraClient jiraClient = Mockito.mock(JiraClient.class);
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);
    when(jiraClient.getIssueCreateMetadata("TJI", "Bug", null, false, false)).thenReturn(jiraIssueCreateMetadataNG);
    JiraUserData jiraUserData = new JiraUserData("accountId", "assignee", true, "your-jira-account-id");
    when(jiraClient.getUsers("your-jira-account-id", null, null)).thenReturn(Arrays.asList(jiraUserData));

    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "accountId");
    fields1.put("Test Summary", "No test added");
    when(jiraClient.createIssue("TJI", "Bug", fields1, true)).thenReturn(jiraIssueNG);
    JiraTaskNGResponse jiraTaskNGResponse = jiraTaskNGHandler.createIssue(jiraTaskNGParameters);

    assertThat(jiraTaskNGResponse.getIssue()).isNotNull();
    assertThat(jiraTaskNGResponse).isNotNull();
    assertThat(jiraTaskNGResponse.getIssue()).isEqualTo(jiraIssueNG);
    assertThat(jiraTaskNGParameters.getFields()).isEqualTo(fields1);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testupdateIssue() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");
    JiraConnectorDTO jiraConnectorDTO =
        JiraConnectorDTO.builder()
            .jiraUrl("https://harness.atlassian.net/")
            .username("username")
            .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
            .build();
    JiraTaskNGParameters jiraTaskNGParameters = JiraTaskNGParameters.builder()
                                                    .jiraConnectorDTO(jiraConnectorDTO)
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
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);
    when(jiraClient.getIssueUpdateMetadata("TJI-37792")).thenReturn(jiraIssueUpdateMetadataNG);
    JiraUserData jiraUserData = new JiraUserData("accountId", "assignee", true, "your-jira-account-id");
    when(jiraClient.getUsers("your-jira-account-id", null, null)).thenReturn(Arrays.asList(jiraUserData));

    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "accountId");
    fields1.put("Test Summary", "No test added");
    when(jiraClient.updateIssue(
             jiraTaskNGParameters.getIssueKey(), jiraTaskNGParameters.getTransitionToStatus(), null, fields1))
        .thenReturn(jiraIssueNG);
    JiraTaskNGResponse jiraTaskNGResponse = jiraTaskNGHandler.updateIssue(jiraTaskNGParameters);

    assertThat(jiraTaskNGResponse.getIssue()).isNotNull();
    assertThat(jiraTaskNGResponse).isNotNull();
    assertThat(jiraTaskNGResponse.getIssue()).isEqualTo(jiraIssueNG);
    assertThat(jiraTaskNGParameters.getFields()).isEqualTo(fields1);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testIssueForException() throws Exception {
    Map<String, String> fields = new HashMap<>();
    fields.put("QE Assignee", "your-jira-account-id");
    fields.put("Test Summary", "No test added");
    JiraConnectorDTO jiraConnectorDTO =
        JiraConnectorDTO.builder()
            .jiraUrl("https://harness.atlassian.net/")
            .username("username")
            .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
            .build();
    JiraTaskNGParameters jiraTaskNGParameters = JiraTaskNGParameters.builder()
                                                    .jiraConnectorDTO(jiraConnectorDTO)
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
    PowerMockito.whenNew(JiraClient.class).withAnyArguments().thenReturn(jiraClient);
    when(jiraClient.getIssueUpdateMetadata("TJI-37792")).thenReturn(jiraIssueUpdateMetadataNG);
    JiraUserData jiraUserData1 = new JiraUserData("accountId1", "assignee1", true, "your-jira-account-id-1");
    JiraUserData jiraUserData2 = new JiraUserData("accountId2", "assignee2", true, "your-jira-account-id-2");
    when(jiraClient.getUsers("your-jira-account-id", null, null))
        .thenReturn(Arrays.asList(jiraUserData1, jiraUserData2));

    JiraIssueNG jiraIssueNG = Mockito.mock(JiraIssueNG.class);
    Map<String, String> fields1 = new HashMap<>();
    fields1.put("QE Assignee", "accountId");
    fields1.put("Test Summary", "No test added");
    when(jiraClient.updateIssue(
             jiraTaskNGParameters.getIssueKey(), jiraTaskNGParameters.getTransitionToStatus(), null, fields1))
        .thenReturn(jiraIssueNG);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> jiraTaskNGHandler.updateIssue(jiraTaskNGParameters));
  }

  private JiraTaskNGParametersBuilder createJiraTaskParametersBuilder() {
    JiraConnectorDTO jiraConnectorDTO =
        JiraConnectorDTO.builder()
            .jiraUrl("https://harness.atlassian.net/")
            .username("username")
            .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
            .build();
    return JiraTaskNGParameters.builder().jiraConnectorDTO(jiraConnectorDTO);
  }
}