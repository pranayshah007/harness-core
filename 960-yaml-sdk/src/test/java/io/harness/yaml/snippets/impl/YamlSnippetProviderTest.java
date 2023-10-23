/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.snippets.impl;

import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.rule.Owner;
import io.harness.yaml.TestClass;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.snippets.dto.YamlSnippetsDTO;
import io.harness.yaml.snippets.helper.YamlSnippetHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class YamlSnippetProviderTest extends CategoryTest {
  private YamlSnippetProvider yamlSnippetProvider;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    YamlSnippetHelper yamlSnippetHelper = new YamlSnippetHelper();
    yamlSnippetProvider = new YamlSnippetProvider(yamlSnippetHelper);
    final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("testIndex.xml");
    String snippetMetaData = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
    final List<YamlSchemaRootClass> yamlSchemaRootClasses =
        Collections.singletonList(YamlSchemaRootClass.builder()
                                      .entityType(EntityType.CONNECTORS)
                                      .clazz(TestClass.ClassWhichContainsInterface.class)
                                      .availableAtAccountLevel(true)
                                      .availableAtOrgLevel(true)
                                      .availableAtProjectLevel(true)
                                      .build());
    yamlSnippetHelper.preComputeTagsAndNameMap(snippetMetaData, yamlSchemaRootClasses.get(0));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlSnippetMetaData() {
    YamlSnippetsDTO yamlSnippetMetaData = yamlSnippetProvider.getYamlSnippetMetaData(Arrays.asList("ConnectorDisconnectHandler", "k8s"));
    assertThat(yamlSnippetMetaData.getYamlSnippets().size()).isEqualTo(1);
    yamlSnippetMetaData = yamlSnippetProvider.getYamlSnippetMetaData(Arrays.asList("ConnectorDisconnectHandler"));
    assertThat(yamlSnippetMetaData.getYamlSnippets().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlSnippet_0() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    final JsonNode yamlSnippet = yamlSnippetProvider.getYamlSnippet("random-1-0", null, null, null);
    final JsonNode yamlSnippet_1 = yamlSnippetProvider.getYamlSnippet("git-1-0", "abc", "xyz", Scope.PROJECT);
    final JsonNode yamlSnippet_2 = yamlSnippetProvider.getYamlSnippet("git-1-0", "abc", null, Scope.ORG);
    final JsonNode yamlSnippet_3 = yamlSnippetProvider.getYamlSnippet("git-1-0", null, null, Scope.PROJECT);
    final JsonNode yamlSnippet_4 = yamlSnippetProvider.getYamlSnippet("git-1-0", null, null, Scope.ORG);
    final JsonNode yamlSnippet_5 = yamlSnippetProvider.getYamlSnippet("git-1-0", null, null, Scope.ACCOUNT);

    assertThat(objectMapper.readTree(IOUtils.resourceToString(
                   "testsnippets/randomSnippet.yaml", StandardCharsets.UTF_8, getClass().getClassLoader())))
        .isEqualTo(yamlSnippet);
    assertThat(yamlSnippet_1.get("ConnectorDisconnectHandler").get(ORG_KEY).textValue()).isEqualTo("abc");
    assertThat(yamlSnippet_1.get("ConnectorDisconnectHandler").get(PROJECT_KEY).textValue()).isEqualTo("xyz");
    assertThat(yamlSnippet_2.get("ConnectorDisconnectHandler").get(ORG_KEY).textValue()).isEqualTo("abc");
    assertThat(yamlSnippet_2.get("ConnectorDisconnectHandler").get(PROJECT_KEY)).isNull();
    assertThat(objectMapper.readTree(IOUtils.resourceToString(
                   "testsnippets/git/test.yaml", StandardCharsets.UTF_8, getClass().getClassLoader())))
        .isEqualTo(yamlSnippet_3);
    assertThat(yamlSnippet_4.get("ConnectorDisconnectHandler").get(PROJECT_KEY)).isNull();
    assertThat(yamlSnippet_4.get("ConnectorDisconnectHandler").get(ORG_KEY)).isNotNull();
    assertThat(yamlSnippet_5.get("ConnectorDisconnectHandler").get(PROJECT_KEY)).isNull();
    assertThat(yamlSnippet_5.get("ConnectorDisconnectHandler").get(ORG_KEY)).isNull();
  }
}
