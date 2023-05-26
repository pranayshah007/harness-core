/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicediscovery;

import io.harness.ng.k8sinlinemanifest.K8sInlineManifestService;
import io.harness.ng.k8sinlinemanifest.K8sInlineManifestServiceImpl;

import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceDiscoveryModule extends AbstractModule {
  private static final AtomicReference<ServiceDiscoveryModule> instanceRef = new AtomicReference<>();

  public static ServiceDiscoveryModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new ServiceDiscoveryModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {
    bind(K8sInlineManifestService.class).to(K8sInlineManifestServiceImpl.class);
  }
}
