/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.entities.DataCollectionTask.Type;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class SLIDataCollectionTaskCreateNextTaskHandler
    implements DataCollectionTaskCreateNextTaskHandler<ServiceLevelIndicator> {
  @Inject private Map<Type, DataCollectionTaskManagementService> dataCollectionTaskServiceMapBinder;

  @Override
  public void handle(ServiceLevelIndicator entity) {
    Preconditions.checkArgument(dataCollectionTaskServiceMapBinder.containsKey(Type.SLI));
    dataCollectionTaskServiceMapBinder.get(Type.SLI).handleCreateNextTask(entity);
  }
}
