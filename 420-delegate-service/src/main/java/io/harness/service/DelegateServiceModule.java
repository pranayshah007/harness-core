package io.harness.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.filter.DelegateFilterPropertiesMapper;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesMapper;
import io.harness.ff.FeatureFlagModule;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.impl.DelegateCallbackRegistryImpl;
import io.harness.service.impl.DelegateInsightsServiceImpl;
import io.harness.service.impl.DelegateNgTokenServiceImpl;
import io.harness.service.impl.DelegateSetupServiceImpl;
import io.harness.service.impl.DelegateTaskSelectorMapServiceImpl;
import io.harness.service.impl.DelegateTaskServiceImpl;
import io.harness.service.impl.DelegateUpgraderServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateInsightsService;
import io.harness.service.intfc.DelegateNgTokenService;
import io.harness.service.intfc.DelegateSetupService;
import io.harness.service.intfc.DelegateTaskSelectorMapService;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.service.intfc.DelegateUpgraderService;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(FeatureFlagModule.getInstance());
    install(FiltersModule.getInstance());

    bind(DelegateTaskService.class).to(DelegateTaskServiceImpl.class);
    bind(DelegateCallbackRegistry.class).to(DelegateCallbackRegistryImpl.class);
    bind(DelegateTaskSelectorMapService.class).to(DelegateTaskSelectorMapServiceImpl.class);
    bind(DelegateInsightsService.class).to(DelegateInsightsServiceImpl.class);
    bind(DelegateCache.class).to(DelegateCacheImpl.class);
    bind(DelegateSetupService.class).to(DelegateSetupServiceImpl.class);
    bind(DelegateUpgraderService.class).to(DelegateUpgraderServiceImpl.class);
    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.DELEGATE.toString()).to(DelegateFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.DELEGATEPROFILE.toString())
        .to(DelegateProfileFilterPropertiesMapper.class);
    bind(DelegateNgTokenService.class).to(DelegateNgTokenServiceImpl.class);
  }
}
