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
import static io.harness.rule.OwnerRule.vivekveman;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraInternalConfig;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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