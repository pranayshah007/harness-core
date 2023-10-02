/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import io.harness.notification.NotificationChannelPersistenceConfig;
import io.harness.springdata.SpringPersistenceConfig;
import io.harness.springdata.SpringPersistenceModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;

public class WingsModulePersistenceModule extends SpringPersistenceModule {
  @Override
  protected Class<?>[] getConfigClasses() {
    List<Class<?>> resultClasses =
        Lists.newArrayList(ImmutableList.of(SpringPersistenceConfig.class, NotificationChannelPersistenceConfig.class));
    Class<?>[] resultClassesArray = new Class<?>[ resultClasses.size() ];
    return resultClasses.toArray(resultClassesArray);
  }
}
