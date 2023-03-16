/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskapps.k8s.application;

import io.harness.delegate.executor.DelegateTaskExecutor;
import io.harness.delegate.executor.bundle.BootstrapBundle;
import io.harness.delegate.task.k8s.K8sTaskNG;
import io.harness.delegate.task.k8s.modules.KubernetesNgTasksModule;
import io.harness.delegate.task.k8s.modules.kryo.K8sNgTaskRegistars;

import software.wings.beans.TaskType;

public class K8sNgApplication extends DelegateTaskExecutor {
  @Override
  public void init(BootstrapBundle taskBundle) {
    taskBundle.registerTask(TaskType.K8S_COMMAND_TASK_NG, K8sTaskNG.class);
    taskBundle.registerKryos(K8sNgTaskRegistars.kryoRegistrars);
    taskBundle.addModule(new KubernetesNgTasksModule());
  }

  public static void main(String[] args) {
    (new K8sNgApplication()).run(args);
  }
}
