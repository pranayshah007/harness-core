/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.SRIDHAR;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;

import java.util.concurrent.Executor;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class TemplateAsyncSetupUsageServiceImplTest extends TemplateServiceTestBase {
  @Mock TemplateReferenceHelper referenceHelper;

  TemplateEntity templateEntity;

  TemplateAsyncSetupUsageService templateAsyncSetupUsageService;

  @Before
  public void setUp() {
    templateEntity = TemplateEntity.builder()
                         .accountId("acc")
                         .orgIdentifier("org")
                         .projectIdentifier("proj")
                         .identifier("pipeline")
                         .yaml("yaml")
                         .build();

    Executor executor = Mockito.mock(Executor.class);
    templateAsyncSetupUsageService = new TemplateAsyncSetupUsageServiceImpl(executor, referenceHelper);
    Reflect.on(templateAsyncSetupUsageService).set("executor", executor);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testPopulateAsyncSetupUsage() {
    SetupUsageParams setupUsageParams = SetupUsageParams.builder().templateEntity(templateEntity).build();
    templateAsyncSetupUsageService.populateAsyncSetupUsage(setupUsageParams);
  }
}
