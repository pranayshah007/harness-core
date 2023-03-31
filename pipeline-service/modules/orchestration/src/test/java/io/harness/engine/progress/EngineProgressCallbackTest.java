/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.progress;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitStatusProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.progress.publisher.ProgressEventPublisher;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ProgressData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineProgressCallbackTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Inject KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Mock ProgressEventPublisher progressEventPublisher;

  private final String nodeExecutionId = generateUuid();

  private EngineProgressCallback engineProgressCallback;

  @Before
  public void setUp() {
    engineProgressCallback =
        EngineProgressCallback.builder()
            .nodeExecutionService(nodeExecutionService)
            .kryoSerializer(kryoSerializer)
            .referenceFalseKryoSerializer(referenceFalseKryoSerializer)
            .progressEventPublisher(progressEventPublisher)
            .ambiance(Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build()).build())
            .build();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowUnsupportedExceptionWhenTestNotify() {
    String correlationId = generateUuid();
    assertThatThrownBy(() -> engineProgressCallback.notify(correlationId, new TestResponseData()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Progress updates are not supported for raw non Binary Response Data");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestNotifyWithUnitProgressData() {
    String correlationId = generateUuid();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(new ArrayList<>()).build();
    BinaryResponseData binaryResponseData =
        BinaryResponseData.builder().data(kryoSerializer.asDeflatedBytes(unitProgressData)).build();

    when(progressEventPublisher.publishEvent(nodeExecutionId, binaryResponseData)).thenReturn(null);

    engineProgressCallback.notify(correlationId, binaryResponseData);

    verify(progressEventPublisher).publishEvent(nodeExecutionId, binaryResponseData);
    verify(nodeExecutionService).updateV2(anyString(), any());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldTestNotifyWithUnitProgressDataUsingKryoWithoutReference() {
    String correlationId = generateUuid();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(new ArrayList<>()).build();
    BinaryResponseData binaryResponseData = BinaryResponseData.builder()
                                                .data(referenceFalseKryoSerializer.asDeflatedBytes(unitProgressData))
                                                .usingKryoWithoutReference(true)
                                                .build();

    when(progressEventPublisher.publishEvent(nodeExecutionId, binaryResponseData)).thenReturn(null);

    engineProgressCallback.notify(correlationId, binaryResponseData);

    verify(progressEventPublisher).publishEvent(nodeExecutionId, binaryResponseData);
    verify(nodeExecutionService).updateV2(anyString(), any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestNotify() {
    String correlationId = generateUuid();
    CommandUnitStatusProgress commandUnitStatusProgress =
        CommandUnitStatusProgress.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    BinaryResponseData binaryResponseData =
        BinaryResponseData.builder().data(kryoSerializer.asDeflatedBytes(commandUnitStatusProgress)).build();

    when(progressEventPublisher.publishEvent(nodeExecutionId, binaryResponseData)).thenReturn(null);

    engineProgressCallback.notify(correlationId, binaryResponseData);

    verify(progressEventPublisher).publishEvent(nodeExecutionId, binaryResponseData);
    verify(nodeExecutionService, never()).updateV2(anyString(), any());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldTestNotifyUsingKryoWithoutReference() {
    String correlationId = generateUuid();
    CommandUnitStatusProgress commandUnitStatusProgress =
        CommandUnitStatusProgress.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    BinaryResponseData binaryResponseData =
        BinaryResponseData.builder()
            .data(referenceFalseKryoSerializer.asDeflatedBytes(commandUnitStatusProgress))
            .usingKryoWithoutReference(true)
            .build();

    when(progressEventPublisher.publishEvent(nodeExecutionId, binaryResponseData)).thenReturn(null);

    engineProgressCallback.notify(correlationId, binaryResponseData);

    verify(progressEventPublisher).publishEvent(nodeExecutionId, binaryResponseData);
    verify(nodeExecutionService, never()).updateV2(anyString(), any());
  }

  private static class TestResponseData implements ProgressData {}
}
