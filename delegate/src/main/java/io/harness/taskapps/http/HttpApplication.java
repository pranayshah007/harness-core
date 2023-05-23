/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskapps.http;

import io.harness.delegate.executor.DelegateTaskExecutor;
import io.harness.delegate.executor.bundle.BootstrapBundle;
import io.harness.delegate.task.http.HttpTaskNG;
import io.harness.delegate.task.http.modules.HttpTaskNGModule;

import software.wings.beans.TaskType;

public class HttpApplication extends DelegateTaskExecutor {
  @Override
  public void init(BootstrapBundle taskBundle) {
    taskBundle.registerTask(TaskType.HTTP_TASK_NG, HttpTaskNG.class);
    taskBundle.registerKryos(HttpTaskKryoRegistrar.kryoRegistrars);
    taskBundle.addModule(new HttpTaskNGModule());
  }

  public static void main(String[] args) {
    (new HttpApplication()).run(args);
  }
}
