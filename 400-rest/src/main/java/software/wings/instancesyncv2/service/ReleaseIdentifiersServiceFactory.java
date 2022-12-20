package software.wings.instancesyncv2.service;

import software.wings.settings.SettingVariableTypes;

import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ReleaseIdentifiersServiceFactory {
  private final CgK8sReleaseIdentifierServiceImpl cgK8sReleaseIdentifierService;

  private final ConcurrentHashMap<SettingVariableTypes, ReleaseIdentifiersService> holder;

  public ReleaseIdentifiersServiceFactory(CgK8sReleaseIdentifierServiceImpl cgK8sReleaseIdentifierService,
      ConcurrentHashMap<SettingVariableTypes, ReleaseIdentifiersService> holder) {
    this.cgK8sReleaseIdentifierService = cgK8sReleaseIdentifierService;
    this.holder = holder;
    initHandlers();
  }

  private void initHandlers() {
    this.holder.put(SettingVariableTypes.KUBERNETES_CLUSTER, cgK8sReleaseIdentifierService);
  }

  public ReleaseIdentifiersService getHandler(SettingVariableTypes cloudProviderType) {
    return this.holder.getOrDefault(cloudProviderType, null);
  }
}
