/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.bundle;

import io.harness.delegate.executor.common.CommonModule;
import io.harness.delegate.executor.serviceproviders.ServiceProvidersModule;
import io.harness.delegate.task.common.DelegateRunnableTask;
import io.harness.serializer.KryoRegistrar;

import software.wings.beans.TaskType;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BootstrapBundle extends AbstractModule {
  private List<Module> modules = new ArrayList<>();
  private Map<TaskType, Class<? extends DelegateRunnableTask>> taskTypeMap = new HashMap<>();
  private Set<Class<? extends KryoRegistrar>> registars;

  public void addModule(Module module) {
    modules.add(module);
  }

  public void registerTaskMap(Map<TaskType, Class<? extends DelegateRunnableTask>> taskMap) {
    taskTypeMap.putAll(taskMap);
  }

  public void registerTask(TaskType type, Class<? extends DelegateRunnableTask> taskClazz) {
    taskTypeMap.put(type, taskClazz);
  }

  public void registerKryos(Set<Class<? extends KryoRegistrar>> registars) {
    this.registars = registars;
  }

  public void configure() {
    installExecutorlibUtilities();
    install(new ExecutorKryoModule(registars));

    modules.forEach(this::install);

    if (!taskTypeMap.isEmpty()) {
      MapBinder<TaskType, Class<? extends DelegateRunnableTask>> mapBinder = MapBinder.newMapBinder(
          binder(), new TypeLiteral<TaskType>() {}, new TypeLiteral<Class<? extends DelegateRunnableTask>>() {});
      taskTypeMap.forEach((taskType, aClass) -> mapBinder.addBinding(taskType).toInstance(aClass));
    }
  }

  private void installExecutorlibUtilities() {
    install(new CommonModule());
    install(new ServiceProvidersModule());
  }
}
