package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

@OwnedBy(CDP)
public class InstanceDetailsFetcherFactory {
  private CgK8sInstancesDetailsFetcher k8sInstancesDetailsFetcher;
  private ConcurrentHashMap<String, InstanceDetailsFetcher> holder;

  @Inject
  public InstanceDetailsFetcherFactory(CgK8sInstancesDetailsFetcher instanceDetailsFetcher) {
    this.holder = new ConcurrentHashMap<>();
    this.k8sInstancesDetailsFetcher = instanceDetailsFetcher;

    initFetchers();
  }

  private void initFetchers() {
    this.holder.put("DIRECT_KUBERNETES", k8sInstancesDetailsFetcher);
  }

  public InstanceDetailsFetcher getFetcher(String cloudProviderType) {
    return this.holder.getOrDefault(cloudProviderType, null);
  }
}
