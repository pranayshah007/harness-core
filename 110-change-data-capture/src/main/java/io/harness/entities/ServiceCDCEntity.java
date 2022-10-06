/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ChangeHandler;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changehandlers.ServicesChangeDataHandler;
import io.harness.changehandlers.TagsInfoCDChangeDataHandler;
import io.harness.ng.core.service.entity.ServiceEntity;

import com.google.inject.Inject;

@OwnedBy(PIPELINE)
public class ServiceCDCEntity implements CDCEntity<ServiceEntity> {
  @Inject private ServicesChangeDataHandler servicesChangeDataHandler;
  @Inject private TagsInfoCDChangeDataHandler tagsInfoCDChangeDataHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("TagsInfoCD")) {
      return tagsInfoCDChangeDataHandler;
    }
    return servicesChangeDataHandler;
  }

  @Override
  public Class<ServiceEntity> getSubscriptionEntity() {
    return ServiceEntity.class;
  }
}
