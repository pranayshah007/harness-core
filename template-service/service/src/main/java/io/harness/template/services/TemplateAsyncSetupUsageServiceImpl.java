/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.Scope;
import io.harness.template.async.TemplateAsyncSetupUsageLogContext;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.async.handler.TemplateReferencesRunnable;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class TemplateAsyncSetupUsageServiceImpl implements TemplateAsyncSetupUsageService {
  private final Executor executor;
  private final TemplateReferenceHelper referenceHelper;
  private final String USE_CASE = "Template Set up Usage Creation Background Task";

  @Inject
  public TemplateAsyncSetupUsageServiceImpl(
      @Named("TemplateAsyncSetupUsageExecutorService") Executor executor, TemplateReferenceHelper referenceHelper) {
    this.executor = executor;
    this.referenceHelper = referenceHelper;
  }

  @Override
  public void populateAsyncSetupUsage(SetupUsageParams setupUsageParams) {
    TemplateEntity templateEntity = setupUsageParams.getTemplateEntity();
    Scope templateScope = Scope.of(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier());
    try (TemplateAsyncSetupUsageLogContext context = new TemplateAsyncSetupUsageLogContext(
             templateScope, templateEntity.getIdentifier(), OVERRIDE_ERROR, USE_CASE)) {
      executor.execute(new TemplateReferencesRunnable(setupUsageParams, referenceHelper));
    } catch (RejectedExecutionException rejectedExecutionException) {
      log.warn("Skipping background task for template {} reference calculation as task queue is full : {}",
          setupUsageParams.getTemplateEntity().getIdentifier(), rejectedExecutionException.getMessage());
    } catch (Exception exception) {
      log.error("Faced exception while submitting background pipeline: {} reference calculation task",
          setupUsageParams.getTemplateEntity().getIdentifier(), exception);
    }
  }
}
