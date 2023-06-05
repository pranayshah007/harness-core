/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertTrue;
import static net.sf.ezmorph.test.ArrayAssertions.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.dto.FetchRemoteEntityRequest;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.GetTemplateEntityRequest;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.resources.beans.yaml.NGTemplateInfoConfig;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.utils.TemplateUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class TemplateMergeServiceHelperTest extends TemplateServiceTestBase {
  @InjectMocks TemplateMergeServiceHelper templateMergeServiceHelper;

  @Mock NGTemplateServiceHelper templateServiceHelper;

  @Mock GitAwareEntityHelper gitAwareEntityHelper;

  YamlNode yamlNode;

  public static final String ACCOUNT_IDENTIFIER = "accountId";
  public static final String ORG_IDENTIFIER = "orgId";
  public static final String PROJECT_IDENTIFIER = "projectId";
  public static final String TEMPLATE_IDENTIFIER = "jan20Stage1";
  public static final String VERSION = "v1";
  public static final String BRANCH = "branch";
  public static final String TEMPLATE_UNIQUE_IDENTIFIER = "accountId/orgId/projectId/jan20Stage1/v1/";
  ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();

  Map<String, YamlNode> templateToGet = new HashMap<>();

  private static final String SAMPLE_YAML = "template:\n"
      + "  name: jan20Stage1\n"
      + "  identifier: jan20Stage1\n"
      + "  versionLabel: v1\n"
      + "  type: Stage\n"
      + "  projectIdentifier: projectId\n"
      + "  orgIdentifier: orgId\n"
      + "  tags: {}\n"
      + "  spec:\n"
      + "    type: Custom\n"
      + "    spec:\n"
      + "      execution:\n"
      + "        steps:\n"
      + "          - step:\n"
      + "              name: s1\n"
      + "              identifier: s1\n"
      + "              template:\n"
      + "                templateRef: jan20Step1\n"
      + "                versionLabel: v1";

  @Before
  public void setup() throws IOException {
    on(templateMergeServiceHelper).set("templateServiceHelper", templateServiceHelper);
    on(templateMergeServiceHelper).set("gitAwareEntityHelper", gitAwareEntityHelper);

    yamlNode = TemplateUtils.validateAndGetYamlNode(SAMPLE_YAML);

    jsonNode.put("templateRef", "jan20Stage1");
    jsonNode.put("versionLabel", "v1");

    templateToGet.put(TEMPLATE_UNIQUE_IDENTIFIER, new YamlNode(jsonNode));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetTemplateUniqueIdentifier() {
    String templateUniqueIdentifier = templateMergeServiceHelper.getTemplateUniqueIdentifier(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, jsonNode);
    assertEquals(TEMPLATE_UNIQUE_IDENTIFIER, templateUniqueIdentifier);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPrepareBatchGetTemplatesRequestForTemplates() {
    Map<String, GetTemplateEntityRequest> requestMap = templateMergeServiceHelper.prepareBatchGetTemplatesRequest(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, templateToGet, false);
    assertTrue(requestMap.containsKey(TEMPLATE_UNIQUE_IDENTIFIER));
    GetTemplateEntityRequest getTemplateEntityRequest = requestMap.get(TEMPLATE_UNIQUE_IDENTIFIER);
    assertEquals(TEMPLATE_IDENTIFIER, getTemplateEntityRequest.getTemplateIdentifier());
    assertEquals(VERSION, getTemplateEntityRequest.getVersion());
    Scope scope = getTemplateEntityRequest.getScope();
    assertEquals(ACCOUNT_IDENTIFIER, scope.getAccountIdentifier());
    assertEquals(ORG_IDENTIFIER, scope.getOrgIdentifier());
    assertEquals(PROJECT_IDENTIFIER, scope.getProjectIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchMetadataAndGetRemoteTemplateList() {
    TemplateEntity stepTemplate = convertYamlToTemplateEntity(SAMPLE_YAML);
    when(templateServiceHelper.getMetadataOrThrowExceptionIfInvalid(any(), any(), any(), any(), any(), eq(false)))
        .thenReturn(Optional.of(stepTemplate));

    Map<String, GetTemplateEntityRequest> getBatchRequest = templateMergeServiceHelper.prepareBatchGetTemplatesRequest(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, templateToGet, false);

    Map<String, TemplateEntity> getBatchTemplateMap = new HashMap<>();
    getBatchTemplateMap.put(TEMPLATE_UNIQUE_IDENTIFIER, stepTemplate);
    when(gitAwareEntityHelper.getWorkingBranch(any())).thenReturn(BRANCH);
    when(templateServiceHelper.getBatchRemoteTemplates(any(), any())).thenReturn(getBatchTemplateMap);

    Queue<YamlField> yamlNodeQueue = new LinkedList<>();

    Map<String, TemplateEntity> remoteTemplatesList = templateMergeServiceHelper.getBatchTemplatesAndProcessTemplates(
        ACCOUNT_IDENTIFIER, getBatchRequest, yamlNodeQueue);
    assertTrue(remoteTemplatesList.containsKey(TEMPLATE_UNIQUE_IDENTIFIER));

    TemplateEntity templateEntity = remoteTemplatesList.get(TEMPLATE_UNIQUE_IDENTIFIER);

    assertEquals(ACCOUNT_IDENTIFIER, templateEntity.getAccountIdentifier());
    assertEquals(ORG_IDENTIFIER, templateEntity.getOrgIdentifier());
    assertEquals(PROJECT_IDENTIFIER, templateEntity.getProjectIdentifier());

    assertEquals(TEMPLATE_IDENTIFIER, templateEntity.getIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetBatchTemplatesAndProcessTemplates() {
    TemplateEntity stepTemplate = convertYamlToTemplateEntity(SAMPLE_YAML);
    stepTemplate.setStoreType(StoreType.INLINE);
    when(templateServiceHelper.getMetadataOrThrowExceptionIfInvalid(any(), any(), any(), any(), any(), eq(false)))
        .thenReturn(Optional.of(stepTemplate));

    Map<String, GetTemplateEntityRequest> getBatchRequest = templateMergeServiceHelper.prepareBatchGetTemplatesRequest(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, templateToGet, false);

    Map<String, TemplateEntity> getBatchTemplateMap = new HashMap<>();
    getBatchTemplateMap.put(TEMPLATE_UNIQUE_IDENTIFIER, stepTemplate);
    when(templateServiceHelper.getBatchRemoteTemplates(any(), any())).thenReturn(getBatchTemplateMap);

    Queue<YamlField> yamlNodeQueue = new LinkedList<>();

    Map<String, TemplateEntity> remoteTemplatesList = templateMergeServiceHelper.getBatchTemplatesAndProcessTemplates(
        ACCOUNT_IDENTIFIER, getBatchRequest, yamlNodeQueue);
    assertTrue(remoteTemplatesList.containsKey(TEMPLATE_UNIQUE_IDENTIFIER));

    TemplateEntity templateEntity = remoteTemplatesList.get(TEMPLATE_UNIQUE_IDENTIFIER);

    assertEquals(ACCOUNT_IDENTIFIER, templateEntity.getAccountIdentifier());
    assertEquals(ORG_IDENTIFIER, templateEntity.getOrgIdentifier());
    assertEquals(PROJECT_IDENTIFIER, templateEntity.getProjectIdentifier());

    assertEquals(TEMPLATE_IDENTIFIER, templateEntity.getIdentifier());
    assertTrue(((YamlField) ((LinkedList<?>) yamlNodeQueue).get(0))
                   .getNode()
                   .getCurrJsonNode()
                   .toString()
                   .contains("templateRef"));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testPerformBatchGetTemplateAndValidate() {
    Map<String, FetchRemoteEntityRequest> remoteTemplatesList = new HashMap<>();

    TemplateEntity stepTemplate = convertYamlToTemplateEntity(SAMPLE_YAML);
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    FetchRemoteEntityRequest fetchRemoteEntityRequest =
        FetchRemoteEntityRequest.builder().scope(scope).entity(stepTemplate).build();
    remoteTemplatesList.put(TEMPLATE_UNIQUE_IDENTIFIER, fetchRemoteEntityRequest);

    Map<String, TemplateEntity> getBatchTemplateMap = new HashMap<>();
    getBatchTemplateMap.put(TEMPLATE_UNIQUE_IDENTIFIER, stepTemplate);
    when(templateServiceHelper.getBatchRemoteTemplates(any(), any())).thenReturn(getBatchTemplateMap);

    Map<String, TemplateEntity> fetchedRemoteTemplateList =
        templateMergeServiceHelper.performBatchGetTemplateAndValidate(ACCOUNT_IDENTIFIER, remoteTemplatesList);
    assertTrue(fetchedRemoteTemplateList.containsKey(TEMPLATE_UNIQUE_IDENTIFIER));
    TemplateEntity fetchedTemplate = fetchedRemoteTemplateList.get(TEMPLATE_UNIQUE_IDENTIFIER);
    assertEquals(fetchedTemplate.getIdentifier(), stepTemplate.getIdentifier());
    assertEquals(fetchedTemplate.getVersion(), stepTemplate.getVersion());
  }

  private TemplateEntity convertYamlToTemplateEntity(String yaml) {
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(yaml, NGTemplateConfig.class);
      NGTemplateInfoConfig templateInfoConfig = templateConfig.getTemplateInfoConfig();
      return TemplateEntity.builder()
          .accountId(ACCOUNT_IDENTIFIER)
          .orgIdentifier(templateInfoConfig.getOrgIdentifier())
          .projectIdentifier(templateInfoConfig.getProjectIdentifier())
          .identifier(templateInfoConfig.getIdentifier())
          .name(templateInfoConfig.getName())
          .yaml(yaml)
          .templateEntityType(templateInfoConfig.getType())
          .versionLabel(templateInfoConfig.getVersionLabel())
          .storeType(StoreType.REMOTE)
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }
}
