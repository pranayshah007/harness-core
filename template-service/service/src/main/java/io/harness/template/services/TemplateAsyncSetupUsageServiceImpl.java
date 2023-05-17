/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.repositories.TemplateSetupUsageEventRepository;
import io.harness.template.async.beans.Action;
import io.harness.template.async.beans.SetupUsageEventStatus;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.async.beans.TemplateSetupUsageEvent;
import io.harness.template.async.handler.TemplateSetupUsageHandler;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
public class TemplateAsyncSetupUsageServiceImpl implements TemplateAsyncSetupUsageService {
  private final Executor executor;
  private final TemplateSetupUsageEventRepository templateSetupUsageEventRepository;
  private final TemplateReferenceHelper referenceHelper;

  @Inject
  public TemplateAsyncSetupUsageServiceImpl(TemplateSetupUsageEventRepository templateSetupUsageEventRepository,
      @Named("TemplateAsyncSetupUsageExecutorService") Executor executor, TemplateReferenceHelper referenceHelper) {
    this.templateSetupUsageEventRepository = templateSetupUsageEventRepository;
    this.executor = executor;
    this.referenceHelper = referenceHelper;
  }

  @Override
  public TemplateSetupUsageEvent startEvent(TemplateEntity entity, String branch, Action action) {
    TemplateSetupUsageEvent templateSetupUsageEvent =
        TemplateSetupUsageEvent.builder()
            .status(SetupUsageEventStatus.INITIATED)
            .action(action)
            .params(SetupUsageParams.builder().templateEntity(entity).build())
            .startTs(System.currentTimeMillis())
            .build();
    TemplateSetupUsageEvent savedTemplateUsageEvent = templateSetupUsageEventRepository.save(templateSetupUsageEvent);

    executor.execute(new TemplateSetupUsageHandler(templateSetupUsageEvent, this, referenceHelper));
    return savedTemplateUsageEvent;
  }

  @Override
  public TemplateSetupUsageEvent updateEvent(String uuid, SetupUsageEventStatus status) {
    Update update = new Update();
    update.set(TemplateSetupUsageEvent.TemplateSetupUsageEventKeys.status, status);
    Criteria criteria = Criteria.where(TemplateSetupUsageEvent.TemplateSetupUsageEventKeys.uuid).is(uuid);

    if (SetupUsageEventStatus.isFinalStatus(status)) {
      update.set(TemplateSetupUsageEvent.TemplateSetupUsageEventKeys.endTs, System.currentTimeMillis());
    }

    return templateSetupUsageEventRepository.update(criteria, update);
  }
}
