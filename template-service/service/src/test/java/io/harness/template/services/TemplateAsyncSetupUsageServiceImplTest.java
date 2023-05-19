/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.anyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.repositories.TemplateSetupUsageEventRepository;
import io.harness.rule.Owner;
import io.harness.template.async.beans.Action;
import io.harness.template.async.beans.SetupUsageEventStatus;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.async.beans.TemplateSetupUsageEvent;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class TemplateAsyncSetupUsageServiceImplTest extends TemplateServiceTestBase {
  @Mock TemplateSetupUsageEventRepository templateSetupUsageEventRepository;

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
    templateAsyncSetupUsageService =
        new TemplateAsyncSetupUsageServiceImpl(templateSetupUsageEventRepository, executor, referenceHelper);
    Reflect.on(templateAsyncSetupUsageService).set("executor", executor);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testServiceLayerStartEvent() {
    TemplateSetupUsageEvent templateSetupUsageEvent =
        TemplateSetupUsageEvent.builder()
            .status(SetupUsageEventStatus.INITIATED)
            .action(Action.GET)
            .params(SetupUsageParams.builder().templateEntity(templateEntity).build())
            .startTs(System.currentTimeMillis())
            .uuid("uuid")
            .build();
    when(templateSetupUsageEventRepository.save(Mockito.any(TemplateSetupUsageEvent.class)))
        .thenReturn(templateSetupUsageEvent);
    TemplateSetupUsageEvent setupUsageEvent =
        templateAsyncSetupUsageService.startEvent(templateEntity, null, Action.GET);
    assertThat(setupUsageEvent).isNotNull();
    assertThat(setupUsageEvent.getUuid()).isNotEmpty();
    assertThat(setupUsageEvent.getStartTs()).isNotNull();
    assertThat(setupUsageEvent.getParams())
        .isEqualTo(SetupUsageParams.builder().templateEntity(templateEntity).build());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testServiceLayerUpdateEvent() {
    TemplateSetupUsageEvent templateSetupUsageEvent =
        TemplateSetupUsageEvent.builder()
            .status(SetupUsageEventStatus.SUCCESS)
            .action(Action.GET)
            .params(SetupUsageParams.builder().templateEntity(templateEntity).build())
            .startTs(System.currentTimeMillis())
            .uuid("uuid")
            .build();
    when(templateSetupUsageEventRepository.update(Mockito.any(), Mockito.any())).thenReturn(templateSetupUsageEvent);
    TemplateSetupUsageEvent setupUsageEvent =
        templateAsyncSetupUsageService.updateEvent("uuid", SetupUsageEventStatus.SUCCESS);
    assertThat(setupUsageEvent).isNotNull();
    assertThat(setupUsageEvent.getUuid()).isNotEmpty();
    assertThat(setupUsageEvent.getStartTs()).isNotNull();
    assertThat(setupUsageEvent.getParams())
        .isEqualTo(SetupUsageParams.builder().templateEntity(templateEntity).build());
  }
}
