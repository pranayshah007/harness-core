/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertTrue;
import static net.sf.ezmorph.test.ArrayAssertions.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.GetTemplateEntityRequest;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.resources.beans.yaml.NGTemplateInfoConfig;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.utils.TemplateUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import org.apache.commons.lang3.tuple.Pair;
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

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testReplaceTemplateOccurrenceWithTemplateSpecYamlV1() {
    String yaml = "version: 1\n"
        + "kind: pipeline\n"
        + "spec:\n"
        + "  inputs:\n"
        + "    http_url:\n"
        + "      type: string\n"
        + "    shell_script:\n"
        + "      type: string\n"
        + "  stages:\n"
        + "    - name: custom_stage_via_template_v1\n"
        + "      type: template\n"
        + "      spec:\n"
        + "        ref: custom_stage_template_v1@V1\n"
        + "        inputs:\n"
        + "          url: <+inputs.http_url>\n"
        + "          script: <+inputs.shell_script>\n";
    JsonNode templateRootNode = YamlUtils.readAsJsonNode(yaml);
    JsonNode stage1 = templateRootNode.get("spec").get("stages").get(0);

    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();

    String template1Yaml = "version: 1\n"
        + "kind: template\n"
        + "spec:\n"
        + "  inputs:\n"
        + "    url:\n"
        + "      type: string\n"
        + "    script:\n"
        + "      type: string\n"
        + "  type: stage\n"
        + "  spec:\n"
        + "    type: custom\n"
        + "    timeout: 30m\n"
        + "    spec:\n"
        + "      steps:\n"
        + "        - type: shell-script\n"
        + "          timeout: 10m\n"
        + "          spec:\n"
        + "            shell: bash\n"
        + "            onDelegate: true\n"
        + "            source:\n"
        + "              type: inline\n"
        + "              spec:\n"
        + "                script: <+inputs.script>\n"
        + "        - type: http\n"
        + "          timeout: 10m\n"
        + "          spec:\n"
        + "            url: <+inputs.url>\n"
        + "            method: GET\n"
        + "            headers: []\n"
        + "            inputVariables: []\n"
        + "            outputVariables: []\n";
    doReturn(Optional.of(TemplateEntity.builder()
                             .harnessVersion(HarnessYamlVersion.V1)
                             .yaml(template1Yaml)
                             .identifier("custom_stage_template_v1")
                             .versionLabel("V1")
                             .build()))
        .when(templateServiceHelper)
        .getTemplateOrThrowExceptionIfInvalid(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "custom_stage_template_v1", "V1", false, false);
    Pair<TemplateEntity, JsonNode> responseMap =
        templateMergeServiceHelper.replaceTemplateOccurrenceWithTemplateSpecYaml(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, stage1, templateCacheMap, false, false, HarnessYamlVersion.V1);
    // <+inputs.script> would have been converted into the <+inputs.shell_script> while resolving inputs
    assertEquals(responseMap.getValue()
                     .get("spec")
                     .get("steps")
                     .get(0)
                     .get("spec")
                     .get("source")
                     .get("spec")
                     .get("script")
                     .asText(),
        "<+inputs.shell_script>");
    // <+inputs.url> would have been converted into the <+inputs.http_url> while resolving inputs
    assertEquals(
        responseMap.getValue().get("spec").get("steps").get(1).get("spec").get("url").asText(), "<+inputs.http_url>");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testReplaceTemplateOccurrenceWithTemplateSpecYamlV0() {
    JsonNode templateJsonNode = YamlUtils.readAsJsonNode("templateRef: custom_stage_template_v0\n"
        + "versionLabel: V1\n"
        + "templateInputs:\n"
        + "  type: Custom\n"
        + "  spec:\n"
        + "    execution:\n"
        + "      steps:\n"
        + "        - step:\n"
        + "            identifier: ShellScript_1\n"
        + "            type: ShellScript\n"
        + "            spec:\n"
        + "              source:\n"
        + "                type: Inline\n"
        + "                spec:\n"
        + "                  script: echo HI\n"
        + "        - step:\n"
        + "            identifier: Http_1\n"
        + "            type: Http\n"
        + "            spec:\n"
        + "              url: https://www.google.com\n");

    TemplateEntity templateEntity = TemplateEntity.builder()
                                        .yaml("template:\n"
                                            + "  name: custom_stage_template_v0\n"
                                            + "  identifier: custom_stage_template_v0\n"
                                            + "  versionLabel: V1\n"
                                            + "  type: Stage\n"
                                            + "  projectIdentifier: Brijesh\n"
                                            + "  orgIdentifier: default\n"
                                            + "  tags: {}\n"
                                            + "  spec:\n"
                                            + "    type: Custom\n"
                                            + "    spec:\n"
                                            + "      execution:\n"
                                            + "        steps:\n"
                                            + "          - step:\n"
                                            + "              type: ShellScript\n"
                                            + "              name: ShellScript_1\n"
                                            + "              identifier: ShellScript_1\n"
                                            + "              spec:\n"
                                            + "                shell: Bash\n"
                                            + "                onDelegate: true\n"
                                            + "                source:\n"
                                            + "                  type: Inline\n"
                                            + "                  spec:\n"
                                            + "                    script: <+input>\n"
                                            + "                environmentVariables: []\n"
                                            + "                outputVariables: []\n"
                                            + "              timeout: 10m\n"
                                            + "          - step:\n"
                                            + "              type: Http\n"
                                            + "              name: Http_1\n"
                                            + "              identifier: Http_1\n"
                                            + "              spec:\n"
                                            + "                url: <+input>\n"
                                            + "                method: GET\n"
                                            + "                headers: []\n"
                                            + "                inputVariables: []\n"
                                            + "                outputVariables: []\n"
                                            + "              timeout: 10s\n")
                                        .identifier("custom_stage_template_v0")
                                        .versionLabel("V1")
                                        .build();
    doReturn(Optional.of(templateEntity))
        .when(templateServiceHelper)
        .getTemplateOrThrowExceptionIfInvalid(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "custom_stage_template_v0", "V1", false, false);

    Pair<TemplateEntity, JsonNode> responseMap =
        templateMergeServiceHelper.replaceTemplateOccurrenceWithTemplateSpecYaml(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, templateJsonNode, new HashMap<>(), false, false, HarnessYamlVersion.V0);
    assertEquals(responseMap.getValue()
                     .get("spec")
                     .get("execution")
                     .get("steps")
                     .get(1)
                     .get("step")
                     .get("spec")
                     .get("url")
                     .asText(),
        "https://www.google.com");

    assertEquals(responseMap.getValue()
                     .get("spec")
                     .get("execution")
                     .get("steps")
                     .get(0)
                     .get("step")
                     .get("spec")
                     .get("source")
                     .get("spec")
                     .get("script")
                     .asText(),
        "echo HI");
  }
}
