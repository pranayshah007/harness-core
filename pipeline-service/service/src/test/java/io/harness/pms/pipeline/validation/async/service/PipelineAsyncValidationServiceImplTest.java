/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.service;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.governance.GovernanceMetadata;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.validation.async.beans.Action;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationParams;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.pipeline.validation.async.handler.PipelineAsyncValidationHandler;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PipelineAsyncValidationServiceImplTest extends PipelineServiceTestBase {
  @Inject @InjectMocks PipelineAsyncValidationServiceImpl asyncValidationService;
  @Mock ExecutorService executorService;

  PipelineEntity pipeline;
  String fqn;

  @Before
  public void setUp() {
    pipeline = PipelineEntity.builder()
                   .accountId("acc")
                   .orgIdentifier("org")
                   .projectIdentifier("proj")
                   .identifier("pipeline")
                   .yaml("yaml")
                   .build();
    fqn = "acc/org/proj/pipeline";
    doReturn(null).when(executorService).submit(any(PipelineAsyncValidationHandler.class));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    PipelineValidationEvent validationEvent = asyncValidationService.startEvent(pipeline, null, Action.CRUD, false);
    assertThat(validationEvent).isNotNull();
    assertThat(validationEvent.getUuid()).isNotEmpty();
    assertThat(validationEvent.getFqn()).isEqualTo(fqn);
    assertThat(validationEvent.getStartTs()).isNotNull();
    assertThat(validationEvent.getParams()).isEqualTo(ValidationParams.builder().pipelineEntity(pipeline).build());
    assertThat(validationEvent.getResult()).isEqualTo(ValidationResult.builder().build());

    String validationEventUuid = validationEvent.getUuid();
    Optional<PipelineValidationEvent> optionalEventByUuid = asyncValidationService.getEventByUuid(validationEventUuid);
    assertThat(optionalEventByUuid.isPresent()).isTrue();
    PipelineValidationEvent eventByUuid = optionalEventByUuid.get();
    assertThat(eventByUuid.getUuid()).isEqualTo(validationEventUuid);
    assertThat(eventByUuid.getFqn()).isEqualTo(fqn);
    assertThat(eventByUuid.getStartTs()).isNotNull();
    assertThat(eventByUuid.getParams()).isEqualTo(ValidationParams.builder().pipelineEntity(pipeline).build());
    assertThat(eventByUuid.getResult()).isEqualTo(ValidationResult.builder().build());

    PipelineValidationEvent updatedEvent = asyncValidationService.updateEvent(
        validationEventUuid, ValidationStatus.SUCCESS, ValidationResult.builder().build());
    assertThat(updatedEvent).isNotNull();
    assertThat(updatedEvent.getUuid()).isEqualTo(validationEventUuid);
    assertThat(updatedEvent.getStatus()).isEqualTo(ValidationStatus.SUCCESS);
    assertThat(updatedEvent.getFqn()).isEqualTo(fqn);
    assertThat(updatedEvent.getStartTs()).isNotNull();
    assertThat(updatedEvent.getParams()).isEqualTo(ValidationParams.builder().pipelineEntity(pipeline).build());
    assertThat(updatedEvent.getResult()).isEqualTo(ValidationResult.builder().build());

    PipelineValidationEvent newEvent = asyncValidationService.startEvent(pipeline, null, Action.CRUD, false);
    assertThat(newEvent).isNotNull();
    String newEventUuid = newEvent.getUuid();
    Optional<PipelineValidationEvent> optionalLatest =
        asyncValidationService.getLatestEventByFQNAndAction(fqn, Action.CRUD);
    assertThat(optionalLatest.isPresent()).isTrue();
    PipelineValidationEvent latestEvent = optionalLatest.get();
    assertThat(latestEvent.getUuid()).isEqualTo(newEventUuid);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateRecordForSuccessfulSyncValidation() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setStatus("PASS").build();
    PipelineValidationEvent validationEvent =
        asyncValidationService.createRecordForSuccessfulSyncValidation(pipeline, null, governanceMetadata, Action.CRUD);
    assertThat(validationEvent.getStatus()).isEqualTo(ValidationStatus.SUCCESS);
    assertThat(validationEvent.getFqn()).isEqualTo(fqn);
    assertThat(validationEvent.getParams().getPipelineEntity()).isEqualTo(pipeline);
    assertThat(validationEvent.getResult().getGovernanceMetadata()).isEqualTo(governanceMetadata);
  }
}