/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.filter.DelegateFilterPropertiesMapper;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesMapper;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.metrics.impl.DelegateMetricsServiceImpl;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.impl.DelegateCallbackRegistryImpl;
import io.harness.service.impl.DelegateInsightsServiceImpl;
import io.harness.service.impl.DelegateMtlsEndpointServiceImpl;
import io.harness.service.impl.DelegateSetupServiceImpl;
import io.harness.service.impl.DelegateTaskSelectorMapServiceImpl;
import io.harness.service.impl.DelegateTaskServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateInsightsService;
import io.harness.service.intfc.DelegateMtlsEndpointService;
import io.harness.service.intfc.DelegateSetupService;
import io.harness.service.intfc.DelegateTaskSelectorMapService;
import io.harness.service.intfc.DelegateTaskService;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceModule extends AbstractModule {
  private final String mtlsSubdomain;

  public DelegateServiceModule() {
    this("");
  }
  public DelegateServiceModule(String mtlsSubdomain) {
    this.mtlsSubdomain = mtlsSubdomain;
  }

  @Provides
  @Named("delegateMtlsSubdomain")
  public String delegateMtlsSubdomain() {
    return this.mtlsSubdomain;
  }

  @Override
  protected void configure() {
    install(FiltersModule.getInstance());

    // register only if mtlsSubdomain is provided - UTs and other services don't need it.
    if (StringUtils.isNotEmpty(this.mtlsSubdomain)) {
      bind(DelegateMtlsEndpointService.class).to(DelegateMtlsEndpointServiceImpl.class);
    }

    bind(DelegateTaskService.class).to(DelegateTaskServiceImpl.class);
    bind(DelegateMetricsService.class).to(DelegateMetricsServiceImpl.class);
    bind(DelegateCallbackRegistry.class).to(DelegateCallbackRegistryImpl.class);
    bind(DelegateTaskSelectorMapService.class).to(DelegateTaskSelectorMapServiceImpl.class);
    bind(DelegateInsightsService.class).to(DelegateInsightsServiceImpl.class);
    bind(DelegateCache.class).to(DelegateCacheImpl.class);
    bind(DelegateSetupService.class).to(DelegateSetupServiceImpl.class);
    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.DELEGATE.toString()).to(DelegateFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.DELEGATEPROFILE.toString())
        .to(DelegateProfileFilterPropertiesMapper.class);
  }
}
