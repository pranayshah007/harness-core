/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateCallbackRecord;
import io.harness.delegate.beans.DelegateNgToken;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.TaskSelectorMap;
import io.harness.delegate.beans.perpetualtask.PerpetualTaskScheduleConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceBeansMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(DelegateAsyncTaskResponse.class);
    set.add(DelegateCallbackRecord.class);
    set.add(DelegateProfile.class);
    set.add(DelegateScope.class);
    set.add(DelegateSyncTaskResponse.class);
    set.add(DelegateTaskProgressResponse.class);
    set.add(TaskSelectorMap.class);
    set.add(DelegateToken.class);
    set.add(PerpetualTaskScheduleConfig.class);
    set.add(DelegateNgToken.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes to register
  }
}
