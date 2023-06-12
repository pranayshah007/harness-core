/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.k8sinlinemanifest;

import io.harness.waiter.NotifyCallback;

public interface K8sInlineManifestService {
  String applyK8sManifest(K8sManifestRequest k8sManifestRequest, String uid, NotifyCallback notifyCallback);
}
