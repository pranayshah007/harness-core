package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  Map<String, ModuleType> typeToService;
  Map<ModuleType, Set<String>> expandableFieldsPerService;

  private String readFile() {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(
          Objects.requireNonNull(classLoader.getResource("opa-pipeline.yaml")), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: opa-pipeline.yaml");
    }
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    typeToService = new HashMap<>();
    typeToService.put("Approval", ModuleType.PMS);
    typeToService.put("HarnessApproval", ModuleType.PMS);
    typeToService.put("JiraApproval", ModuleType.PMS);
    typeToService.put("Deployment", ModuleType.CD);
    typeToService.put("Http", ModuleType.PMS);
    doReturn(typeToService).when(expansionRequestsHelper).getTypeToService();

    expandableFieldsPerService = new HashMap<>();
    expandableFieldsPerService.put(ModuleType.PMS, Collections.singleton("connectorRef"));
    expandableFieldsPerService.put(
        ModuleType.CD, new HashSet<>(Arrays.asList("connectorRef", "serviceRef", "environmentRef")));
    doReturn(expandableFieldsPerService).when(expansionRequestsHelper).getExpandableFieldsPerService();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchExpansionRequests() {
    String pipelineYaml = readFile();
    assertThat(pipelineYaml).isNotNull();
    Set<ExpansionRequest> expansionRequests = expansionRequestsExtractor.fetchExpansionRequests(pipelineYaml);
    assertThat(expansionRequests).hasSize(6);
    assertThat(expansionRequests)
        .contains(ExpansionRequest.builder()
                      .module(ModuleType.PMS)
                      .fqn("pipeline/stages/[0]/stage/spec/execution/steps/[1]/step/spec/connectorRef")
                      .fieldValue(new TextNode("jira_basic"))
                      .build(),
            ExpansionRequest.builder()
                .module(ModuleType.PMS)
                .fqn("pipeline/stages/[1]/stage/spec/execution/steps/[1]/step/spec/connectorRef")
                .fieldValue(new TextNode("<+input>"))
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn("pipeline/stages/[1]/stage/spec/serviceConfig/serviceRef")
                .fieldValue(new TextNode("goodUpserteh"))
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn("pipeline/stages/[1]/stage/spec/infrastructure/infrastructureDefinition/spec/connectorRef")
                .fieldValue(new TextNode("temp"))
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn("pipeline/stages/[1]/stage/spec/infrastructure/environmentRef")
                .fieldValue(new TextNode("PR_ENV"))
                .build(),
            ExpansionRequest.builder()
                .module(ModuleType.CD)
                .fqn(
                    "pipeline/stages/[1]/stage/spec/serviceConfig/serviceDefinition/spec/artifacts/primary/spec/connectorRef")
                .fieldValue(new TextNode("nvh_docker"))
                .build());
  }
}