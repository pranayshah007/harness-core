/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.module;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.filter.DelegateFilterPropertiesMapper;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesMapper;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.metrics.impl.DelegateMetricsServiceImpl;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.service.impl.DelegateAuthServiceImpl;
import io.harness.service.impl.DelegateCallbackRegistryImpl;
import io.harness.service.impl.DelegateSetupServiceImpl;
import io.harness.service.impl.DelegateTaskSelectorMapServiceImpl;
import io.harness.service.impl.DelegateTaskServiceImpl;
import io.harness.service.impl.TaskProgressServiceImpl;
import io.harness.service.intfc.DelegateAuthService;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateSetupService;
import io.harness.service.intfc.DelegateTaskSelectorMapService;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.service.intfc.TaskProgressService;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(FiltersModule.getInstance());
    bind(DelegateTaskService.class).to(DelegateTaskServiceImpl.class);
    bind(DelegateMetricsService.class).to(DelegateMetricsServiceImpl.class);
    bind(DelegateCallbackRegistry.class).to(DelegateCallbackRegistryImpl.class);
    bind(DelegateTaskSelectorMapService.class).to(DelegateTaskSelectorMapServiceImpl.class);
    bind(TaskProgressService.class).to(TaskProgressServiceImpl.class);
    bind(DelegateSetupService.class).to(DelegateSetupServiceImpl.class);
    bind(DelegateAuthService.class).to(DelegateAuthServiceImpl.class);
    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.DELEGATE.toString()).to(DelegateFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.DELEGATEPROFILE.toString())
        .to(DelegateProfileFilterPropertiesMapper.class);
  }
}
