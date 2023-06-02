/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ExpansionRequestsExtractorTest extends CategoryTest {
  @InjectMocks ExpansionRequestsExtractor expansionRequestsExtractor;
  @Mock ExpansionRequestsHelper expansionRequestsHelper;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;

  Map<String, ModuleType> typeToService;
  Map<ModuleType, Set<String>> expandableFieldsPerService;
  List<LocalFQNExpansionInfo> localFQNRequestMetadata;
  String pipelineYaml;
  String pipelineYamlWithParallelStages;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: opa-pipeline.yaml");
    }
  }

  @Before
  public void setUp() {
    pipelineYaml = readFile("opa-pipeline.yaml");
    pipelineYamlWithParallelStages = readFile("pipeline-extensive.yml");
    MockitoAnnotations.initMocks(this);
    typeToService = new HashMap<>();
    when(pmsSdkInstanceService.getActiveInstances()).thenReturn(new ArrayList<>());
    typeToService.put("Approval", ModuleType.PMS);
    typeToService.put("HarnessApproval", ModuleType.PMS);
    typeToService.put("JiraApproval", ModuleType.PMS);
    typeToService.put("Deployment", ModuleType.CD);
    typeToService.put("Http", ModuleType.PMS);
    doReturn(typeToService).when(expansionRequestsHelper).getTypeToService(any());

    expandableFieldsPerService = new HashMap<>();
    expandableFieldsPerService.put(ModuleType.PMS, Collections.singleton("connectorRef"));
    expandableFieldsPerService.put(
        ModuleType.CD, new HashSet<>(Arrays.asList("connectorRef", "serviceRef", "environmentRef")));
    doReturn(expandableFieldsPerService).when(expansionRequestsHelper).getExpandableFieldsPerService(any());

    LocalFQNExpansionInfo sloExpansion =
        LocalFQNExpansionInfo.builder().module(ModuleType.CV).stageType("Deployment").localFQN("stage/spec").build();
    LocalFQNExpansionInfo effExpansion = LocalFQNExpansionInfo.builder()
                                             .module(ModuleType.CE)
                                             .stageType("Deployment")
                                             .localFQN("stage/spec/execution")
                                             .build();
    LocalFQNExpansionInfo stageExpansion =
        LocalFQNExpansionInfo.builder().module(ModuleType.CD).stageType("Deployment").localFQN("stage").build();
    localFQNRequestMetadata = Arrays.asList(sloExpansion, effExpansion, stageExpansion);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchExpansionRequests() {
    doReturn(Collections.emptyList()).when(expansionRequestsHelper).getLocalFQNRequestMetadata(any());
    Set<ExpansionRequest> expansionRequests = expansionRequestsExtractor.fetchExpansionRequests(pipelineYaml);
    assertThat(expansionRequests).hasSize(5);
    assertThat(expansionRequests)
        .contains(ExpansionRequest.builder()
                      .module(ModuleType.PMS)
                      .fqn("pipeline/stages/[0]/stage/spec/execution/steps/[1]/step/spec/connectorRef")
                      .key("connectorRef")
                      .fieldValue(new TextNode("jira_basic"))
                      .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn("pipeline/stages/[1]/stage/spec/serviceConfig/serviceRef")
                .key("serviceRef")
                .fieldValue(new TextNode("goodUpserteh"))
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn("pipeline/stages/[1]/stage/spec/infrastructure/infrastructureDefinition/spec/connectorRef")
                .fieldValue(new TextNode("temp"))
                .key("connectorRef")
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn("pipeline/stages/[1]/stage/spec/infrastructure/environmentRef")
                .key("environmentRef")
                .fieldValue(new TextNode("PR_ENV"))
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn(
                    "pipeline/stages/[1]/stage/spec/serviceConfig/serviceDefinition/spec/artifacts/primary/spec/connectorRef")
                .key("connectorRef")
                .fieldValue(new TextNode("nvh_docker"))
                .build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExpressionsAsExpandableFields() throws IOException {
    Stack<ModuleType> namespace = new Stack<>();
    namespace.push(ModuleType.PMS);
    Set<ExpansionRequest> serviceCalls = new HashSet<>();
    String testYaml = "spec:\n"
        + "  connectorRef: <+input>\n"
        + "  spec:\n"
        + "    connectorRef: <+spec.connectorRef>\n"
        + "    spec:\n"
        + "      connectorRef: notAnExpr";
    YamlNode testNode = YamlUtils.readTree(testYaml).getNode();
    expansionRequestsExtractor.getServiceCallsForObject(
        testNode, expandableFieldsPerService, typeToService, namespace, serviceCalls);
    assertThat(serviceCalls).hasSize(1);
    ExpansionRequest request = new ArrayList<>(serviceCalls).get(0);
    assertThat(request.getFqn()).isEqualTo("spec/spec/spec/connectorRef");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFQNBasedServiceCalls() throws IOException {
    doReturn(localFQNRequestMetadata).when(expansionRequestsHelper).getLocalFQNRequestMetadata(any());

    YamlNode pipelineNode = YamlUtils.readTree(pipelineYaml).getNode();
    Set<ExpansionRequest> serviceCalls = new HashSet<>();
    expansionRequestsExtractor.getFQNBasedServiceCalls(pipelineNode, localFQNRequestMetadata, serviceCalls);
    assertThat(serviceCalls).hasSize(3);
    List<ExpansionRequest> serviceCallsList = new ArrayList<>(serviceCalls);
    List<ModuleType> selectedModules =
        serviceCallsList.stream().map(serviceCall -> serviceCall.getModule()).collect(Collectors.toList());
    assertThat(selectedModules).contains(ModuleType.CD, ModuleType.CV, ModuleType.CE);
    for (ExpansionRequest expansionRequest : serviceCallsList) {
      switch (expansionRequest.getModule()) {
        case CD:
          assertThat(expansionRequest.getFqn()).isEqualTo("pipeline/stages/[1]/stage");
          break;
        case CV:
          assertThat(expansionRequest.getFqn()).isEqualTo("pipeline/stages/[1]/stage/spec");
          break;
        case CE:
          assertThat(expansionRequest.getFqn()).isEqualTo("pipeline/stages/[1]/stage/spec/execution");
          break;
        default:
      }
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFQNBasedServiceCallForParallelStages() throws IOException {
    doReturn(localFQNRequestMetadata).when(expansionRequestsHelper).getLocalFQNRequestMetadata(any());

    YamlNode pipelineNode = YamlUtils.readTree(pipelineYamlWithParallelStages).getNode();
    Set<ExpansionRequest> serviceCalls = new HashSet<>();
    expansionRequestsExtractor.getFQNBasedServiceCalls(pipelineNode, localFQNRequestMetadata, serviceCalls);
    List<String> serviceCallsFQNs = serviceCalls.stream().map(ExpansionRequest::getFqn).collect(Collectors.toList());
    assertThat(serviceCallsFQNs).hasSize(12);
    assertThat(serviceCallsFQNs)
        .contains("pipeline/stages/[0]/stage/spec", "pipeline/stages/[0]/stage/spec/execution",
            "pipeline/stages/[1]/parallel/[0]/stage/spec", "pipeline/stages/[1]/parallel/[0]/stage/spec/execution",
            "pipeline/stages/[1]/parallel/[1]/stage/spec", "pipeline/stages/[1]/parallel/[1]/stage/spec/execution",
            "pipeline/stages/[2]/stage/spec", "pipeline/stages/[2]/stage/spec/execution", "pipeline/stages/[0]/stage",
            "pipeline/stages/[1]/parallel/[0]/stage", "pipeline/stages/[1]/parallel/[1]/stage",
            "pipeline/stages/[2]/stage");
  }
}
