/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Store;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.util.CallGeneratorParams;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class NamespaceFetcher {
  private final Store<V1Namespace> store;
  private final CoreV1Api coreV1Api;

  @Inject
  public NamespaceFetcher(@Assisted ApiClient apiClient, @Assisted SharedInformerFactory sharedInformerFactory) {
    this.coreV1Api = new CoreV1Api(apiClient);

    this.store =
        sharedInformerFactory
            .sharedIndexInformerFor((CallGeneratorParams callGeneratorParams)
                                        -> this.coreV1Api.listNamespaceCall(null, null, null, null, null, null,
                                            callGeneratorParams.resourceVersion, null,
                                            callGeneratorParams.timeoutSeconds, callGeneratorParams.watch, null),
                V1Namespace.class, V1NamespaceList.class)
            .getIndexer();
  }

  public V1Namespace getNamespaceByKey(String namespaceName) throws ApiException {
    if (this.store.getByKey(namespaceName) != null) {
      return this.store.getByKey(namespaceName);
    }

    log.warn("Namespace not found in NamespaceFetcher store, fetching using coreV1Api");
    return this.coreV1Api.readNamespace(namespaceName, null);
  }
}
