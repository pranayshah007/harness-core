/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.PipelineServiceTestBase;
import io.harness.PipelineUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.groovy.util.Maps;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineEnforcementServiceImplTest extends PipelineServiceTestBase {
  private static final String accountId = "ACCOUNT_ID";
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock EnforcementClientService enforcementClientService;
  @Mock CommonStepInfo commonStepInfo;
  @Mock PmsSdkHelper pmsSdkHelper;
  @InjectMocks PipelineEnforcementServiceImpl pipelineEnforcementService;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetFeatureRestrictionMap() {
    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST1);
    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST1, true);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThat(pipelineEnforcementService.getFeatureRestrictionMap(accountId,
                   featureRestrictionNames.stream().map(FeatureRestrictionName::toString).collect(Collectors.toSet())))
        .isEqualTo(featureRestrictionNameBooleanMap);

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestriction() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(
        SdkStep.newBuilder()
            .setStepType(stepType)
            .setStepInfo(StepInfo.newBuilder().setFeatureRestrictionName(FeatureRestrictionName.TEST5.name()).build())
            .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST1, true);
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType));

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestrictionThrowsException() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(SdkStep.newBuilder()
                       .setStepType(stepType)
                       .setStepInfo(StepInfo.newBuilder()
                                        .setName("test 5")
                                        .setFeatureRestrictionName(FeatureRestrictionName.TEST5.name())
                                        .build())
                       .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap =
        Maps.of(FeatureRestrictionName.TEST5, false);
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThatThrownBy(
        () -> pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType)))
        .isInstanceOf(FeatureNotSupportedException.class)
        .hasMessage(
            "Your current plan does not support the use of following steps: [test 5].Please upgrade your plan.");

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestrictionThrowsExceptionOnlyDeployment() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(SdkStep.newBuilder()
                       .setStepType(stepType)
                       .setStepInfo(StepInfo.newBuilder()
                                        .setName("test 5")
                                        .setFeatureRestrictionName(FeatureRestrictionName.TEST5.name())
                                        .build())
                       .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST5, true);
    when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    when(enforcementClientService.getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType));

    verify(enforcementClientService)
        .getAvailabilityForRemoteFeatures(new ArrayList<>(featureRestrictionNames), accountId);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModulesFromCache() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    getStageTypeCache().clear();
    assertThat(pipelineEnforcementService.populateModulesFromCache(stageFields, modules)).isFalse();
    assertThat(modules).isEmpty();

    getStageTypeCache().put("Custom", "Custom");
    getStageTypeCache().put("Approval", "Approval");
    assertThat(pipelineEnforcementService.populateModulesFromCache(stageFields, modules)).isTrue();
    assertThat(modules).hasSize(2);
    assertThat(modules).containsExactlyInAnyOrder("Custom", "Approval");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModuleAndUpdateCacheWhenUnsupportedFields() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    Map<String, PlanCreatorServiceInfo> services =
        Collections.singletonMap("theKey", new PlanCreatorServiceInfo(null, null));
    when(pmsSdkHelper.getServices()).thenReturn(services);

    getStageTypeCache().clear();
    pipelineEnforcementService.populateModuleAndUpdateCache(stageFields, modules);
    assertThat(modules).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModuleAndUpdateCacheWhenSupportedFields() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put("stage", ImmutableSet.of("Approval", "Custom"));

    Map<String, PlanCreatorServiceInfo> services =
        Collections.singletonMap("theKey", new PlanCreatorServiceInfo(supportedTypes, null));
    when(pmsSdkHelper.getServices()).thenReturn(services);

    getStageTypeCache().clear();
    pipelineEnforcementService.populateModuleAndUpdateCache(stageFields, modules);
    assertThat(modules).hasSize(1);
    assertThat(modules).containsExactlyInAnyOrder("theKey");

    assertThat(getStageTypeCache()).hasSize(2);
    assertThat(getStageTypeCache()).containsKeys("Custom", "Approval");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyPopulateModuleAndUpdateCacheWhenDoesNotMatterSupportedFields() throws IOException {
    Set<YamlField> stageFields = getStageFields("pipeline-enforcement-modules.yaml");
    Set<String> modules = new HashSet<>();

    Map<String, PlanCreatorServiceInfo> services =
        Collections.singletonMap("theKey", new PlanCreatorServiceInfo(null, null));
    when(pmsSdkHelper.getServices()).thenReturn(services);

    getStageTypeCache().clear();
    getStageTypeCache().put("Custom", "Custom");
    getStageTypeCache().put("Approval", "Approval");

    pipelineEnforcementService.populateModuleAndUpdateCache(stageFields, modules);
    assertThat(modules).hasSize(2);
    assertThat(modules).containsExactlyInAnyOrder("Custom", "Approval");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotEnforceUnknowModules() {
    Map<String, Document> filters = new HashMap<>();
    filters.put("ABC", new Document());
    filters.put("DEF", new Document());

    PipelineEntity pipelineEntity = PipelineEntity.builder().accountId("ACCOUNT_ID").filters(filters).build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateExecutionForKnowModules() {
    Map<String, Document> filters = new HashMap<>();
    filters.put("CD", new Document());
    filters.put("CI", new Document());

    PipelineEntity pipelineEntity = PipelineEntity.builder().accountId("ACCOUNT_ID").filters(filters).build();
    pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

    ArgumentCaptor<List<FeatureRestrictionName>> argCaptor = ArgumentCaptor.forClass(List.class);
    verify(enforcementClientService).getAvailabilityForRemoteFeatures(argCaptor.capture(), eq("ACCOUNT_ID"));
    assertThat(argCaptor.getValue()).hasSize(2);
    assertThat(argCaptor.getValue())
        .containsExactlyInAnyOrder(FeatureRestrictionName.DEPLOYMENTS_PER_MONTH, FeatureRestrictionName.BUILDS);
  }

  private Map<String, String> getStageTypeCache() {
    return (Map<String, String>) ReflectionUtils.getFieldValue(pipelineEnforcementService, "stageTypeToModule");
  }

  private Set<YamlField> getStageFields(String yamlFile) throws IOException {
    String yamlContent = readFile(yamlFile);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlField pipelineField = yamlField.getNode().getField("pipeline");

    return PipelineUtils.getStagesFieldFromPipeline(pipelineField);
  }

  private String readFile(String filePath) throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    return Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
  }
}
