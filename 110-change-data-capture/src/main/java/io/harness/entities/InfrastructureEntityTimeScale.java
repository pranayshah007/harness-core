/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.InfrastructureChangeDataHandler;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;

import com.google.inject.Inject;

public class InfrastructureEntityTimeScale implements CDCEntity<InfrastructureEntity> {
  @Inject private InfrastructureChangeDataHandler infrastructureChangeDataHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    return infrastructureChangeDataHandler;
  }

  @Override
  public Class<InfrastructureEntity> getSubscriptionEntity() {
    return InfrastructureEntity.class;
  }
}
