/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviserParameters;
import io.harness.pms.sdk.core.adviser.markFailure.OnMarkFailureAdviserParameters;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviserParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.failurestrategy.abort.v1.AbortFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.ignore.v1.IgnoreFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualFailureSpecConfigV1;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualInterventionFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.markFailure.v1.MarkAsFailFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.marksuccess.v1.MarkAsSuccessFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureSpecConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureTypeV1;
import io.harness.yaml.core.timeout.Timeout;

import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class PlanCreatorUtilsV1Test extends PmsSdkCoreTestBase {
  @Mock KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetAdviserObtainmentsForStage() throws IOException {
    String nextNodeId = "nextNodeId";
    byte[] adviserParamsBytes = "AdviserParams".getBytes();
    assertThat(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, null).size()).isEqualTo(0);
    assertThat(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, Dependency.newBuilder().build()).size())
        .isEqualTo(0);

    assertThat(
        PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer,
            Dependency.newBuilder()
                .setNodeMetadata(HarnessStruct.newBuilder().putData("abc", HarnessValue.newBuilder().build()).build())
                .build()))
        .size()
        .isEqualTo(0);

    doReturn(nextNodeId).when(kryoSerializer).asObject(nextNodeId.getBytes());
    doReturn(adviserParamsBytes)
        .when(kryoSerializer)
        .asBytes(NextStepAdviserParameters.builder().nextNodeId(nextNodeId).build());
    List<AdviserObtainment> adviserObtainments = PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer,
        Dependency.newBuilder()
            .setNodeMetadata(
                HarnessStruct.newBuilder()
                    .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextNodeId).build())
                    .build())
            .build());
    assertThat(adviserObtainments.size()).isEqualTo(1);
    assertThat(adviserObtainments.get(0).getType())
        .isEqualTo(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build());
    assertThat(adviserObtainments.get(0).getParameters()).isEqualTo(ByteString.copyFrom(adviserParamsBytes));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetFailureStrategies() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String fileName = "failure-strategies-v1.yaml";
    String failureStrategiesYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(fileName)), StandardCharsets.UTF_8);
    List<FailureConfigV1> failureConfigs =
        PlanCreatorUtilsV1.getFailureStrategies(new YamlNode(YamlUtils.readAsJsonNode(failureStrategiesYaml)));
    assertEquals(failureConfigs,
        List.of(
            FailureConfigV1.builder()
                .errors(List.of(NGFailureTypeV1.AUTHORIZATION_ERROR))
                .action(
                    RetryFailureActionConfigV1.builder()
                        .spec(RetryFailureSpecConfigV1.builder()
                                  .attempts(ParameterField.createValueField(2))
                                  .interval(ParameterField.createValueField(List.of(Timeout.fromString("1m"))))
                                  .failure(RetryFailureConfigV1.builder()
                                               .action(ManualInterventionFailureActionConfigV1.builder()
                                                           .spec(ManualFailureSpecConfigV1.builder()
                                                                     .timeout(ParameterField.createValueField(
                                                                         Timeout.fromString("10s")))
                                                                     .timeout_action(
                                                                         AbortFailureActionConfigV1.builder().build())
                                                                     .build())
                                                           .build())
                                               .build())
                                  .build())
                        .build())
                .build()));
    fileName = "failure-strategies-list-v1.yaml";
    failureStrategiesYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(fileName)), StandardCharsets.UTF_8);
    failureConfigs =
        PlanCreatorUtilsV1.getFailureStrategies(new YamlNode(YamlUtils.readAsJsonNode(failureStrategiesYaml)));
    assertEquals(failureConfigs,
        List.of(FailureConfigV1.builder()
                    .errors(List.of(NGFailureTypeV1.AUTHENTICATION_ERROR, NGFailureTypeV1.CONNECTIVITY_ERROR))
                    .action(IgnoreFailureActionConfigV1.builder().build())
                    .build(),
            FailureConfigV1.builder()
                .errors(List.of(NGFailureTypeV1.ALL_ERRORS))
                .action(MarkAsSuccessFailureActionConfigV1.builder().build())
                .build()));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetFailureStrategiesAdvisers() {
    Dependency dependency = Dependency.newBuilder().build();
    String nextNodeUuid = UUIDGenerator.generateUuid();
    List<FailureConfigV1> stepFailureStrategies = new ArrayList<>();
    assertThat(PlanCreatorUtilsV1
                   .getFailureStrategiesAdvisers(kryoSerializer, dependency, stepFailureStrategies, nextNodeUuid, false)
                   .isEmpty())
        .isTrue();

    OnMarkFailureAdviserParameters onMarkFailureAdviserParameters =
        OnMarkFailureAdviserParameters.builder()
            .applicableFailureTypes(NGFailureTypeV1.ALL_ERRORS.getFailureTypes())
            .nextNodeId(nextNodeUuid)
            .build();
    doReturn(Collections.singletonList(FailureConfigV1.builder()
                                           .errors(Collections.singletonList(NGFailureTypeV1.ALL_ERRORS))
                                           .action(MarkAsFailFailureActionConfigV1.builder().build())
                                           .build()))
        .when(kryoSerializer)
        .asInflatedObject("DummyForStage".getBytes());
    doReturn("DummyForStage".getBytes()).when(kryoSerializer).asBytes(onMarkFailureAdviserParameters);
    dependency = dependency.toBuilder()
                     .setParentInfo(HarnessStruct.newBuilder()
                                        .putData(PlanCreatorConstants.STAGE_FAILURE_STRATEGIES,
                                            HarnessValue.newBuilder()
                                                .setBytesValue(ByteString.copyFrom("DummyForStage".getBytes()))
                                                .build())
                                        .build())
                     .build();

    List<AdviserObtainment> adviserObtainments = PlanCreatorUtilsV1.getFailureStrategiesAdvisers(
        kryoSerializer, dependency, stepFailureStrategies, nextNodeUuid, false);

    assertThat(adviserObtainments.get(0).getType().getType()).isEqualTo("MARK_AS_FAILURE");
    assertThat(new String(adviserObtainments.get(0).getParameters().toByteArray())).isEqualTo("DummyForStage");

    doReturn(Collections.singletonList(FailureConfigV1.builder()
                                           .errors(Collections.singletonList(NGFailureTypeV1.ALL_ERRORS))
                                           .action(MarkAsSuccessFailureActionConfigV1.builder().build())
                                           .build()))
        .when(kryoSerializer)
        .asInflatedObject("DummyForGroup".getBytes());
    dependency = dependency.toBuilder()
                     .setParentInfo(HarnessStruct.newBuilder()
                                        .putData(PlanCreatorConstants.STEP_GROUP_FAILURE_STRATEGIES,
                                            HarnessValue.newBuilder()
                                                .setBytesValue(ByteString.copyFrom("DummyForGroup".getBytes()))
                                                .build())
                                        .build())
                     .build();

    OnMarkSuccessAdviserParameters onMarkSuccessAdviserParameters =
        OnMarkSuccessAdviserParameters.builder()
            .applicableFailureTypes(NGFailureTypeV1.ALL_ERRORS.getFailureTypes())
            .nextNodeId(nextNodeUuid)
            .build();
    doReturn("DummyForGroup".getBytes()).when(kryoSerializer).asBytes(onMarkSuccessAdviserParameters);
    adviserObtainments = PlanCreatorUtilsV1.getFailureStrategiesAdvisers(
        kryoSerializer, dependency, stepFailureStrategies, nextNodeUuid, false);
    assertThat(adviserObtainments.get(0).getType().getType()).isEqualTo("MARK_SUCCESS");
    assertThat(new String(adviserObtainments.get(0).getParameters().toByteArray())).isEqualTo("DummyForGroup");

    OnAbortAdviserParameters onAbortAdviserParameters =
        OnAbortAdviserParameters.builder().applicableFailureTypes(NGFailureTypeV1.ALL_ERRORS.getFailureTypes()).build();
    doReturn("DummyForStep".getBytes()).when(kryoSerializer).asBytes(onAbortAdviserParameters);
    stepFailureStrategies.add(FailureConfigV1.builder()
                                  .errors(Collections.singletonList(NGFailureTypeV1.ALL_ERRORS))
                                  .action(AbortFailureActionConfigV1.builder().build())
                                  .build());
    adviserObtainments = PlanCreatorUtilsV1.getFailureStrategiesAdvisers(
        kryoSerializer, dependency, stepFailureStrategies, nextNodeUuid, false);
    assertThat(adviserObtainments.get(0).getType().getType()).isEqualTo("ABORT");
    assertThat(new String(adviserObtainments.get(0).getParameters().toByteArray())).isEqualTo("DummyForStep");
  }
}
