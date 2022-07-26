/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.CFApi;
import io.harness.cf.openapi.ApiClient;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.ApprovalInstanceServiceImpl;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.steps.barriers.service.BarrierServiceImpl;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceServiceImpl;
import io.harness.steps.resourcerestraint.service.ResourceRestraintRegistry;
import io.harness.steps.resourcerestraint.service.ResourceRestraintRegistryImpl;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationStepsModule extends AbstractModule {
  private final OrchestrationStepConfig configuration;
  private static OrchestrationStepsModule instance;

  public OrchestrationStepsModule(OrchestrationStepConfig configuration) {
    this.configuration = configuration;
  }

  public static OrchestrationStepsModule getInstance(OrchestrationStepConfig orchestrationStepConfig) {
    if (instance == null) {
      instance = new OrchestrationStepsModule(orchestrationStepConfig);
    }
    return instance;
  }

  @Override
  protected void configure() {
    bind(BarrierService.class).to(BarrierServiceImpl.class);
    bind(ResourceRestraintService.class).to(ResourceRestraintServiceImpl.class);
    bind(ResourceRestraintInstanceService.class).to(ResourceRestraintInstanceServiceImpl.class);
    bind(ResourceRestraintRegistry.class).to(ResourceRestraintRegistryImpl.class);
    bind(ApprovalInstanceService.class).to(ApprovalInstanceServiceImpl.class);
  }

  @Provides
  @Singleton
  @Named("cfPipelineAPI")
  CFApi providesCfAPI() {
    ApiClient apiClient = new ApiClient();
    if (configuration != null) {
      apiClient.setBasePath(configuration.getFfServerBaseUrl());
      apiClient.setVerifyingSsl(configuration.getFfServerSSLVerify());
    }
    return new CFApi(apiClient);
  }

  @Provides
  @Singleton
  public OrchestrationStepConfig orchestrationStepsConfig() {
    return configuration;
  }
}
