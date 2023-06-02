/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.async.AsyncCreatorBaseEventHandler;
import io.harness.async.AsyncCreatorResponse;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.manage.ManagedExecutorService;
import io.harness.pms.contracts.plan.CreatePartialPlanEvent;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.PartialPlanResponse;
import io.harness.pms.sdk.core.plan.creation.PlanCreationResponseBlobHelper;
import io.harness.pms.sdk.core.plan.creation.beans.MergePlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PlanCreatorService;
import io.harness.pms.yaml.YamlField;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class CreatePartialPlanEventHandler
    extends AsyncCreatorBaseEventHandler<CreatePartialPlanEvent, PlanCreationContext> {
  private final Executor executor = new ManagedExecutorService(Executors.newFixedThreadPool(2));
  @Inject PlanCreatorService planCreatorService;

  @Inject PartialPlanResponseEventPublisher partialPlanResponseEventPublisher;
  @Inject PlanCreationResponseBlobHelper planCreationResponseBlobHelper;

  @Override
  @NonNull
  protected Map<String, String> extraLogProperties(CreatePartialPlanEvent event) {
    return new HashMap<>();
  }

  @Override
  protected Dependencies extractDependencies(CreatePartialPlanEvent message) {
    return message.getDeps();
  }

  @Override
  protected PlanCreationContext extractContext(CreatePartialPlanEvent message) {
    return PlanCreationContext.builder().globalContext(message.getContextMap()).build();
  }

  @Override
  protected void handleResult(CreatePartialPlanEvent event, AsyncCreatorResponse creatorResponse) {
    MergePlanCreationResponse finalResponse = (MergePlanCreationResponse) creatorResponse;
    if (EmptyPredicate.isNotEmpty(finalResponse.getErrorMessages())) {
      partialPlanResponseEventPublisher.publishEvent(
          PartialPlanResponse.newBuilder()
              .setNotifyId(event.getNotifyId())
              .setErrorResponse(ErrorResponse.newBuilder().addAllMessages(finalResponse.getErrorMessages()).build())
              .build());
    } else {
      partialPlanResponseEventPublisher.publishEvent(
          PartialPlanResponse.newBuilder()
              .setNotifyId(event.getNotifyId())
              .setBlobResponse(planCreationResponseBlobHelper.toBlobResponse(finalResponse))
              .build());
    }
  }

  @Override
  protected AsyncCreatorResponse createNewAsyncCreatorResponse(PlanCreationContext ctx) {
    return PlanCreationResponse.builder().contextMap(ctx.getGlobalContext()).build();
  }

  @Override
  public Dependencies handleDependencies(
      PlanCreationContext ctx, AsyncCreatorResponse finalUncastedResponse, Dependencies dependencies) {
    return planCreatorService.createPlanForDependencies(
        ctx, (MergePlanCreationResponse) finalUncastedResponse, dependencies, new HashMap<>());
  }

  @Override
  protected void handleException(CreatePartialPlanEvent event, YamlField field, Exception ex) {
    WingsException processedException = exceptionManager.processException(ex);
    partialPlanResponseEventPublisher.publishEvent(
        PartialPlanResponse.newBuilder()
            .setNotifyId(event.getNotifyId())
            .setErrorResponse(
                ErrorResponse.newBuilder().addMessages(ExceptionUtils.getMessage(processedException)).build())
            .build());
  }

  @Override
  protected void handleException(CreatePartialPlanEvent event, Exception ex) {
    WingsException processedException = exceptionManager.processException(ex);
    partialPlanResponseEventPublisher.publishEvent(
        PartialPlanResponse.newBuilder()
            .setNotifyId(event.getNotifyId())
            .setErrorResponse(
                ErrorResponse.newBuilder().addMessages(ExceptionUtils.getMessage(processedException)).build())
            .build());
  }
}
