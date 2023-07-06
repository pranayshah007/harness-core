/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.PipelineServiceTestHelper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.RollbackExecutionInfo;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.steps.matrix.StrategyStep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryExecuteHelperTest extends CategoryTest {
  @InjectMocks private RetryExecutionHelper retryExecuteHelper;
  @Mock private NodeExecutionServiceImpl nodeExecutionService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private PMSPipelineService pipelineService;
  @Mock private PMSExecutionService executionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;

  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String pipelineId = "pipeline";
  String planExecId = "plan";

  String branch = "branch";

  String repoName = "repoName";

  String filepath = "filepath";

  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  List<RetryStageInfo> getFirstStageFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(100L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getlastStageFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .nextId("stage3")
                         .parentId("parent2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(300L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getFirstStageParallelAndFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent1")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent1")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(300L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getlastStageParallelAndFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent1")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent1")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent2")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent2")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage6")
                         .identifier("stage6")
                         .parentId("parent2")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(600L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage7")
                         .identifier("stage7")
                         .parentId("parent3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(700L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage8")
                         .identifier("stage8")
                         .parentId("parent3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(800L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage9")
                         .identifier("stage9")
                         .parentId("parent3")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(900L)
                         .build());

    return stageDetails;
  }

  private List<RetryStageInfo> getMixTypeStagesWithParallelFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent2")
                         .nextId("stage3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent4")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage6")
                         .identifier("stage6")
                         .parentId("parent4")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(600L)
                         .build());

    return stageDetails;
  }

  private List<RetryStageInfo> getMixTypeStagesWithSeriesStageFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent2")
                         .nextId("stage3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent4")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent4")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage6")
                         .identifier("stage6")
                         .parentId("parent4")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(600L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage7")
                         .identifier("stage7")
                         .parentId("parent7")
                         .nextId("stage8")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage8")
                         .identifier("stage8")
                         .parentId("parent8")
                         .nextId("stage9")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage9")
                         .identifier("stage9")
                         .parentId("parent9")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(600L)
                         .build());
    return stageDetails;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesSeries() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();

    // passing empty stageDetails
    RetryInfo retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().size()).isEqualTo(0);

    // making first stage as empty
    stageDetails = getFirstStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().get(0).getInfo()).isEqualTo(stageDetails);

    // making the last stageFailed
    stageDetails = getlastStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().size()).isEqualTo(3);
    assertThat(retryInfo.getGroups().get(0).getInfo().get(0)).isEqualTo(stageDetails.get(0));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesParallel() {
    List<RetryStageInfo> stageDetails;
    RetryInfo retryInfo;

    // making first stage as parallel and failed
    stageDetails = getFirstStageParallelAndFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    List<RetryGroup> retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.get(0).getInfo()).isEqualTo(stageDetails);

    // having more than once parallel stages. All stages in parallel
    stageDetails = getlastStageParallelAndFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.size()).isEqualTo(3);
    assertThat(retryGroupList.get(0).getInfo().size()).isEqualTo(3);
    assertThat(retryGroupList.get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryGroupList.get(1).getInfo().get(0).getIdentifier()).isEqualTo("stage4");
    assertThat(retryGroupList.get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage7");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesSeriesAndParallel() {
    List<RetryStageInfo> stageDetails;
    RetryInfo retryInfo;

    // parallel step failed after getting success for stages in series
    stageDetails = getMixTypeStagesWithParallelFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    List<RetryGroup> retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.size()).isEqualTo(4);
    assertThat(retryGroupList.get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryGroupList.get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage3");
    assertThat(retryGroupList.get(3).getInfo().size()).isEqualTo(3);

    // series stage failed having few stages in parallel before
    stageDetails = getMixTypeStagesWithSeriesStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.size()).isEqualTo(7);
    assertThat(retryGroupList.get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryGroupList.get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage3");
    assertThat(retryGroupList.get(3).getInfo().size()).isEqualTo(3);
    assertThat(retryGroupList.get(6).getInfo().get(0).getIdentifier()).isEqualTo("stage9");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateRetry() {
    // empty and null yaml values
    assertThat(retryExecuteHelper.validateRetry("updatedYaml", "")).isEqualTo(false);
    assertThat(retryExecuteHelper.validateRetry(null, "originalYaml")).isEqualTo(false);

    // same yaml
    String updatedYamlFile = "retry-updated1.yaml";
    String updatedYaml = readFile(updatedYamlFile);

    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml, originalYaml)).isEqualTo(true);

    // updated the yaml - adding a stage
    // same yaml
    String updatedYamlFile2 = "retry-updated2.yaml";
    String updatedYaml2 = readFile(updatedYamlFile2);

    String originalYamlFile2 = "retry-original2.yaml";
    String originalYaml2 = readFile(originalYamlFile2);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml2, originalYaml2)).isEqualTo(false);

    // added step in on of the stage and changed the name of the stage
    String updatedYamlFile3 = "retry-updated3.yaml";
    String updatedYaml3 = readFile(updatedYamlFile3);

    String originalYamlFile3 = "retry-original3.yaml";
    String originalYaml3 = readFile(originalYamlFile3);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml3, originalYaml3)).isEqualTo(true);

    // updated the identifier
    String updatedYamlFile4 = "retry-updated4.yaml";
    String updatedYaml4 = readFile(updatedYamlFile4);

    String originalYamlFile4 = "retry-original4.yaml";
    String originalYaml4 = readFile(originalYamlFile4);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml4, originalYaml4)).isEqualTo(false);

    // shuffling of stages
    String updatedYamlFile5 = "retry-updated5.yaml";
    String updatedYaml5 = readFile(updatedYamlFile5);

    String originalYamlFile5 = "retry-original5.yaml";
    String originalYaml5 = readFile(originalYamlFile5);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml5, originalYaml5)).isEqualTo(false);

    // adding the stage in parallel
    String updatedYamlFile6 = "retry-updated6.yaml";
    String updatedYaml6 = readFile(updatedYamlFile6);

    String originalYamlFile6 = "retry-original6.yaml";
    String originalYaml6 = readFile(originalYamlFile6);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml6, originalYaml6)).isEqualTo(false);

    // shuffling of parallel stages
    String updatedYamlFile7 = "retry-updated7.yaml";
    String updatedYaml7 = readFile(updatedYamlFile7);

    String originalYamlFile7 = "retry-original7.yaml";
    String originalYaml7 = readFile(originalYamlFile7);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml7, originalYaml7)).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testRetryProcessedYaml() throws IOException {
    String previousYamlFile = "retry-processedYamlPrevious1.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrent1.yaml";
    String currentYaml = readFile(currentYamlFile);
    String resultYamlFile = "retry-processedYamlResult1.yaml";
    String resultYaml = readFile(resultYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();
    String replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, Collections.singletonList("stage2"), identifierOfSkipStages, PipelineVersion.V0);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // resuming from the first stage
    resultYamlFile = "retry-processedYamlResultFirstStageFailed1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, Collections.singletonList("stage1"), new ArrayList<>(), PipelineVersion.V0);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // failing a single stage which is ahead of some parallel stages
    String previousGoldenYamlFile = "retry-processedYamlPreviousGolden.yaml";
    String previousGoldenYaml = readFile(previousGoldenYamlFile);
    String currentGoldenYamlFile = "retry-processedYamlCurrentGolden.yaml";
    String currentGoldenYaml = readFile(currentGoldenYamlFile);
    String resultProcessedFile = "retry-processedYamlResultGolden1.yaml";
    String resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml,
        Collections.singletonList("stage7"), new ArrayList<>(), PipelineVersion.V0);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // failing single stages from parallel groups
    resultProcessedFile = "retry-processedYamlResultSingleStageFailedInParallelStages.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml,
        Collections.singletonList("stage9"), new ArrayList<>(), PipelineVersion.V0);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // failing multiple stage failure in parallel group
    resultProcessedFile = "retry-processedYamlResultMultipleStageFailedInParallelStages.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml,
        Arrays.asList("stage3", "stage5"), new ArrayList<>(), PipelineVersion.V0);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // selecting all stages in parallel group
    resultProcessedFile = "retry-processedYamlResultAllStageFailedInParallelStages.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml,
        Arrays.asList("stage3", "stage4", "stage5"), new ArrayList<>(), PipelineVersion.V0);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // testing the matrix scenarios
    // Resuming from the stage that has strategy in it.
    previousYaml = readFile("retry/previous-retry-processed-yaml-with-matrix.yaml");
    currentYaml = readFile("retry/current-processed-yaml-with-matrix.yaml");
    resultProcessedYaml = readFile("retry/result-processed-yaml-with-matrix.yaml");
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, Collections.singletonList("approval"), Collections.emptyList(), PipelineVersion.V0);
    assertEquals(replacedProcessedYaml, resultProcessedYaml);

    // Resuming from the next stage of the stage that has strategy.
    previousYaml = readFile("retry/previous-retry-processed-yaml-with-matrix-1.yaml");
    currentYaml = readFile("retry/current-processed-yaml-with-matrix-1.yaml");
    resultProcessedYaml = readFile("retry/result-processed-yaml-with-matrix-1.yaml");
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, Collections.singletonList("sssss"), new ArrayList<>(), PipelineVersion.V0);
    assertEquals(replacedProcessedYaml, resultProcessedYaml);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForV1() throws IOException {
    String previousYamlFile = "retry-processedYamlPreviousV1.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrentV1.yaml";
    String currentYaml = readFile(currentYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();

    // Retrying from stage1 that was passed in previous execution.
    String resultYamlFile = "retry-processedYamlResultV1.yaml";
    String resultYaml = readFile(resultYamlFile);
    String replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, List.of("stage1"), identifierOfSkipStages, PipelineVersion.V1);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // Retrying from parallel stages stage2_1 and stage2_2. Only one of these stages were failed. But in retry both the
    // stages will run.
    resultYamlFile = "retry-processedYamlResult1V1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, List.of("stage2_1", "stage2_2"), identifierOfSkipStages, PipelineVersion.V1);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // Retrying from parallel stages stage2_1 only. Only one of these stages were failed. And one will run while retry.
    resultYamlFile = "retry-processedYamlResult2V1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, List.of("stage2_2"), identifierOfSkipStages, PipelineVersion.V1);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // Retrying from parallel stages stage1_1 and stage1_2. Both were success in previous execuiton.
    resultYamlFile = "retry-processedYamlResult3V1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, List.of("stage1_1", "stage1_2"), identifierOfSkipStages, PipelineVersion.V1);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);
  }

  private String yamlToJsonString(String resultProcessedYaml) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readTree(resultProcessedYaml).toString();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testIsFailedStatus() {
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.EXPIRED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.ABORTED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.FAILED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.APPROVAL_REJECTED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.APPROVALREJECTED)).isEqualTo(true);

    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.SUCCESS)).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchOnlyFailedStages() {
    List<RetryStageInfo> retryStageInfos = new ArrayList<>();
    List<String> stageIdentifier = new ArrayList<>();
    assertThatThrownBy(() -> retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, stageIdentifier))
        .isInstanceOf(InvalidRequestException.class);

    // testing caching of exception
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage1").build());
    stageIdentifier.add("stage2");
    assertThatThrownBy(() -> retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, stageIdentifier))
        .isInstanceOf(InvalidRequestException.class);

    stageIdentifier.clear();
    retryStageInfos.clear();

    // testing whole valid status
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage1").status(ExecutionStatus.SUCCESS).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage2").status(ExecutionStatus.ABORTED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage3").status(ExecutionStatus.IGNOREFAILED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage4").status(ExecutionStatus.FAILED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage5").status(ExecutionStatus.EXPIRED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage6").status(ExecutionStatus.APPROVALREJECTED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage7").status(ExecutionStatus.APPROVALREJECTED).build());

    stageIdentifier.add("stage1");
    stageIdentifier.add("stage2");
    stageIdentifier.add("stage3");
    stageIdentifier.add("stage4");
    stageIdentifier.add("stage5");
    stageIdentifier.add("stage6");
    stageIdentifier.add("stage7");

    List<String> onlyFailedStageIdentifier = retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, stageIdentifier);
    assertThat(onlyFailedStageIdentifier.size()).isEqualTo(5);
    assertThat(onlyFailedStageIdentifier).contains("stage2");
    assertThat(onlyFailedStageIdentifier).contains("stage4");
    assertThat(onlyFailedStageIdentifier).contains("stage5");
    assertThat(onlyFailedStageIdentifier).contains("stage6");
    assertThat(onlyFailedStageIdentifier).contains("stage7");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchUuidOfNonRetryStages() throws IOException {
    String previousYamlFile = "retry-processedYamlPrevious1.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrent1.yaml";
    String currentYaml = readFile(currentYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();
    retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, Collections.singletonList("stage2"), identifierOfSkipStages, PipelineVersion.V0);

    // resuming from the first stage
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml(
        previousYaml, currentYaml, Collections.singletonList("stage1"), identifierOfSkipStages, PipelineVersion.V0);
    assertThat(identifierOfSkipStages.size()).isEqualTo(0);

    // failing a single stage which is ahead of some parallel stages
    identifierOfSkipStages.clear();
    String previousGoldenYamlFile = "retry-processedYamlPreviousGolden.yaml";
    String previousGoldenYaml = readFile(previousGoldenYamlFile);
    String currentGoldenYamlFile = "retry-processedYamlCurrentGolden.yaml";
    String currentGoldenYaml = readFile(currentGoldenYamlFile);
    retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml, Collections.singletonList("stage7"),
        identifierOfSkipStages, PipelineVersion.V0);
    assertThat(identifierOfSkipStages.size()).isEqualTo(6);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("stage3");
    assertThat(identifierOfSkipStages.get(3)).isEqualTo("stage4");
    assertThat(identifierOfSkipStages.get(4)).isEqualTo("stage5");
    assertThat(identifierOfSkipStages.get(5)).isEqualTo("stage6");

    // failing single stages from parallel groups
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml, Collections.singletonList("stage9"),
        identifierOfSkipStages, PipelineVersion.V0);
    assertThat(identifierOfSkipStages.size()).isEqualTo(8);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("stage3");
    assertThat(identifierOfSkipStages.get(3)).isEqualTo("stage4");
    assertThat(identifierOfSkipStages.get(4)).isEqualTo("stage5");
    assertThat(identifierOfSkipStages.get(5)).isEqualTo("stage6");
    assertThat(identifierOfSkipStages.get(6)).isEqualTo("stage7");
    assertThat(identifierOfSkipStages.get(7)).isEqualTo("stage8");

    // failing multiple stage failure in parallel group
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml, Arrays.asList("stage3", "stage5"),
        identifierOfSkipStages, PipelineVersion.V0);
    assertThat(identifierOfSkipStages.size()).isEqualTo(3);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("stage4");

    // selecting all stages in parallel group
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml(previousGoldenYaml, currentGoldenYaml,
        Arrays.asList("stage3", "stage4", "stage5"), identifierOfSkipStages, PipelineVersion.V0);
    assertThat(identifierOfSkipStages.size()).isEqualTo(2);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testTransformPlan() {
    StepType TEST_STEP_TYPE =
        StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();
    String uuid = "uuid1";
    List<String> identifierOfSkipStages = Collections.singletonList(uuid);
    List<String> stageIdentifierToRetryWith = Collections.singletonList("stage3");

    when(nodeExecutionService.fetchStageFqnFromStageIdentifiers(any(), eq(identifierOfSkipStages)))
        .thenReturn(Collections.singletonList("pipeline.stages.pip1"));

    PlanNode planNode1 =
        PlanNode.builder()
            .name("Test Node")
            .uuid(uuid)
            .identifier("test")
            .stageFqn("pipeline.stages.pip1")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    Map<String, Node> uuidMapper = new HashMap<>();
    uuidMapper.put("nodeUuid", planNode1);
    when(nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(any(), any())).thenReturn(uuidMapper);

    // Returning emptyList. So strategy node should not get converted to IdentityNode.
    doReturn(Collections.emptyList()).when(nodeExecutionService).fetchStrategyNodeExecutions(any(), any());
    PlanNode planNode2 =
        PlanNode.builder()
            .name("Test Node2")
            .uuid("uuid2")
            .identifier("test2")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    PlanNode planNode3 =
        PlanNode.builder()
            .name("Test Node3")
            .uuid("uuid3")
            .identifier("test3")
            .stageFqn("pipeline.stages.stage3")
            .stepType(StrategyStep.STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    Plan newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3)).build(), identifierOfSkipStages, "abc",
        stageIdentifierToRetryWith);

    List<Node> updatedNodes = newPlan.getPlanNodes();
    List<Node> identityPlanNodes =
        updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());
    assertThat(updatedNodes.size()).isEqualTo(3);
    assertThat(updatedNodes.get(0).getNodeType()).isEqualTo(NodeType.PLAN_NODE);
    assertEquals(identityPlanNodes.size(), 1);
    assertThat(((IdentityPlanNode) identityPlanNodes.get(0)).getOriginalNodeExecutionId()).isEqualTo("nodeUuid");
    assertThat(identityPlanNodes.get(0).getIdentifier()).isEqualTo("test");
    assertThat(identityPlanNodes.get(0).getName()).isEqualTo("Test Node");
    assertThat(identityPlanNodes.get(0).getUuid()).isEqualTo(uuid);

    List<Node> strategyNodes =
        updatedNodes.stream().filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE)).collect(Collectors.toList());
    assertEquals(strategyNodes.size(), 1);
    // This would be PlanNode because previous noExecutions did not have strategy node for provided stageFqn.
    assertEquals(strategyNodes.get(0).getNodeType(), NodeType.PLAN_NODE);

    doReturn(Collections.singletonList("pipeline.stages.stage3"))
        .when(nodeExecutionService)
        .fetchStageFqnFromStageIdentifiers(any(), eq(stageIdentifierToRetryWith));
    // StrategyNode should get converted to IdentityNode now.
    doReturn(Collections.singletonList(NodeExecution.builder()
                                           .ambiance(Ambiance.newBuilder()
                                                         .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                                         .addLevels(Level.newBuilder().build())
                                                         .build())
                                           .stageFqn("pipeline.stages.stage3")
                                           .planNode(planNode3)
                                           .build()))
        .when(nodeExecutionService)
        .fetchStrategyNodeExecutions(any(), any());

    newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3)).build(), identifierOfSkipStages, "abc",
        stageIdentifierToRetryWith);

    updatedNodes = newPlan.getPlanNodes();
    identityPlanNodes = updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());

    assertEquals(identityPlanNodes.size(), 2);
    assertThat(((IdentityPlanNode) identityPlanNodes.get(0)).getOriginalNodeExecutionId()).isEqualTo("nodeUuid");
    assertThat(identityPlanNodes.get(0).getIdentifier()).isEqualTo("test");
    assertThat(identityPlanNodes.get(0).getName()).isEqualTo("Test Node");
    assertThat(identityPlanNodes.get(0).getUuid()).isEqualTo(uuid);

    strategyNodes = identityPlanNodes.stream()
                        .filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE))
                        .collect(Collectors.toList());
    assertEquals(strategyNodes.size(), 1);
    // This would be of IdentityPlanNode type. Previous nodeExecutions has strategyNode with provided stageFqn.
    assertEquals(strategyNodes.get(0).getNodeType(), NodeType.IDENTITY_PLAN_NODE);
    assertThat(strategyNodes.get(0).getIdentifier()).isEqualTo(planNode3.getIdentifier());
    assertThat(strategyNodes.get(0).getName()).isEqualTo(planNode3.getName());
    assertThat(strategyNodes.get(0).getUuid()).isEqualTo(planNode3.getUuid());
    assertThat(((IdentityPlanNode) strategyNodes.get(0)).getUseAdviserObtainments()).isTrue();
    assertThat(strategyNodes.get(0).getAdviserObtainments()).isEqualTo(planNode3.getAdviserObtainments());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetHistory() {
    String rootExecutionId = "rootExecutionId";
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        Collections.singletonList(PipelineExecutionSummaryEntity.builder().build());

    // entities are <=1. Checking error message
    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()));
    RetryHistoryResponseDto retryHistory = retryExecuteHelper.getRetryHistory(rootExecutionId, "planExecutionId");
    assertThat(retryHistory.getErrorMessage()).isNotNull();

    pipelineExecutionSummaryEntities = Arrays.asList(PipelineExecutionSummaryEntity.builder()
                                                         .planExecutionId("uuid1")
                                                         .startTs(10L)
                                                         .endTs(11L)
                                                         .status(ExecutionStatus.FAILED)
                                                         .build(),
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("uuid2")
            .startTs(20L)
            .endTs(21L)
            .status(ExecutionStatus.FAILED)
            .build(),
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("uuid3")
            .startTs(30L)
            .endTs(31L)
            .status(ExecutionStatus.ABORTED)
            .build());

    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()));
    doReturn(Optional.of(PlanExecutionMetadata.builder().build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId("planExecutionId");
    retryHistory = retryExecuteHelper.getRetryHistory(rootExecutionId, "planExecutionId");
    assertThat(retryHistory.getErrorMessage()).isNull();
    assertThat(retryHistory.getLatestExecutionId()).isEqualTo("uuid1");
    assertThat(retryHistory.getExecutionInfos().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetLatestExecutionId() {
    String rootExecutionId = "rootExecutionId";
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        Collections.singletonList(PipelineExecutionSummaryEntity.builder().build());

    // entities are <=1. Checking error message
    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()));
    RetryLatestExecutionResponseDto retryLatestExecutionResponse =
        retryExecuteHelper.getRetryLatestExecutionId(rootExecutionId);
    assertThat(retryLatestExecutionResponse.getErrorMessage()).isNotNull();

    pipelineExecutionSummaryEntities = Arrays.asList(PipelineExecutionSummaryEntity.builder()
                                                         .planExecutionId("uuid1")
                                                         .startTs(10L)
                                                         .endTs(11L)
                                                         .status(ExecutionStatus.FAILED)
                                                         .build(),
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("uuid2")
            .startTs(20L)
            .endTs(21L)
            .status(ExecutionStatus.FAILED)
            .build(),
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("uuid3")
            .startTs(30L)
            .endTs(31L)
            .status(ExecutionStatus.ABORTED)
            .build());

    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()));
    retryLatestExecutionResponse = retryExecuteHelper.getRetryLatestExecutionId(rootExecutionId);
    assertThat(retryLatestExecutionResponse.getErrorMessage()).isNull();
    assertThat(retryLatestExecutionResponse.getLatestExecutionId()).isEqualTo("uuid1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWithPipelineDeleted() {
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .entityGitDetails(buildEntityGitDetails())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    doReturn(Optional.empty()).when(pipelineService).getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    RetryInfo retryInfo = retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage())
        .isEqualTo("Pipeline with the given ID: pipeline does not exist or has been deleted");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenNotTheLatestExecution() {
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    doReturn(PipelineExecutionSummaryEntity.builder().isLatestExecution(false).build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    RetryInfo retryInfo = retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage())
        .isEqualTo(
            "This execution is not the latest of all retried execution. You can only retry the latest execution.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryForExecutionsThatHaveUndergonePRB() {
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .rollbackExecutionInfo(RollbackExecutionInfo.builder().rollbackModeExecutionId("something").build())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    RetryInfo retryInfo = retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage())
        .isEqualTo("This execution has undergone Pipeline Rollback, and hence cannot be retried.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenThirtyDaysHavePassed() {
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - 60 * DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false");
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage()).isEqualTo("Execution is more than 30 days old. Cannot retry");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenPlanExecutionDoesNotExist() {
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    doReturn(Optional.empty()).when(planExecutionMetadataService).findByPlanExecutionId(planExecId);

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false");
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage()).isEqualTo("No Plan Execution exists for id plan");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenNoChangeInPipeline() {
    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    doReturn(Optional.of(PipelineEntity.builder().yaml(originalYaml).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    doReturn(
        Optional.of(PlanExecutionMetadata.builder()
                        .yaml(originalYaml)
                        .stagesExecutionMetadata(StagesExecutionMetadata.builder().isStagesExecution(false).build())
                        .build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(planExecId);

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false");
    assertThat(retryInfo.isResumable()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryForTriggerExecution() {
    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    doReturn(Optional.of(PipelineEntity.builder().yaml(originalYaml).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    doReturn(Optional.of(PlanExecutionMetadata.builder().yaml(originalYaml).build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(planExecId);

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false");
    assertThat(retryInfo.isResumable()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryForSelectiveStageExecution() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      description: desc>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      description: desc\n";

    String s2StageYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "      description: \"desc\"\n";

    doReturn(Optional.of(PipelineEntity.builder().yaml(pipelineYaml).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .entityGitDetails(buildEntityGitDetails())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecId, false);
    doReturn(Optional.of(PlanExecutionMetadata.builder()
                             .yaml(s2StageYaml)
                             .stagesExecutionMetadata(StagesExecutionMetadata.builder()
                                                          .isStagesExecution(true)
                                                          .stageIdentifiers(Collections.singletonList("s2"))
                                                          .fullPipelineYaml(pipelineYaml)
                                                          .build())
                             .build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(planExecId);

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false");
    assertThat(retryInfo.isResumable()).isTrue();
  }

  private EntityGitDetails buildEntityGitDetails() {
    return EntityGitDetails.builder().branch(branch).repoName(repoName).filePath(filepath).build();
  }
}
