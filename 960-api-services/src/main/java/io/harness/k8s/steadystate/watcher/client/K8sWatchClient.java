/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.supplier.ThrowingSupplier;

import io.kubernetes.client.common.KubernetesObject;
import java.lang.reflect.Type;
import okhttp3.Call;

@OwnedBy(HarnessTeam.CDP)
public interface K8sWatchClient {
  <T extends KubernetesObject> boolean waitOnCondition(
      Type type, ThrowingSupplier<Call> callSupplier, K8sEventPredicate<T> condition) throws Exception;
}
