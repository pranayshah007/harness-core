/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.async.handler;

import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.services.TemplateAsyncSetupUsageService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TemplateSetupUsageHandlerTest {
  TemplateReferencesRunnable templateSetupUsageHandler;
  @Mock TemplateAsyncSetupUsageService templateAsyncSetupUsageService;

  TemplateEntity templateEntity;
  @Mock TemplateReferenceHelper referenceHelper;
  SetupUsageParams setupUsageParams;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    templateEntity = TemplateEntity.builder().accountId("acc").orgIdentifier("org").projectIdentifier("proj").build();
    setupUsageParams = SetupUsageParams.builder().templateEntity(templateEntity).build();
    templateSetupUsageHandler = new TemplateReferencesRunnable(setupUsageParams, referenceHelper);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testSuccessfulRun() {
    templateSetupUsageHandler.run();
    verify(referenceHelper, times(1)).populateTemplateReferences(setupUsageParams);
  }
}
