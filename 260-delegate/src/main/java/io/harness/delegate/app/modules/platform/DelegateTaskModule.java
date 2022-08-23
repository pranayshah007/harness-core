/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import io.harness.delegate.task.DelegateRunnableTask;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.ShellScriptTask;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DelegateTaskModule extends AbstractModule {
  @Override
  protected void configure() {
    MapBinder<TaskType, Class<? extends DelegateRunnableTask>> mapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<TaskType>() {}, new TypeLiteral<Class<? extends DelegateRunnableTask>>() {});

    mapBinder.addBinding(TaskType.SCRIPT).toInstance(ShellScriptTask.class);
  }
}
