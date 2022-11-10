/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import software.wings.search.entities.application.ApplicationTimeScaleEntity;
import software.wings.search.entities.cloudprovider.CloudProviderTimeScaleEntity;
import software.wings.search.entities.deployment.DeploymentExecutionEntity;
import software.wings.search.entities.deployment.DeploymentStepExecutionEntity;
import software.wings.search.entities.environment.EnvironmentTimeScaleEntity;
import software.wings.search.entities.executionInterrupt.ExecutionInterruptEntity;
import software.wings.search.entities.infradefinition.InfrastructureDefinitionTimeScaleEntity;
import software.wings.search.entities.pipeline.PipelineTimeScaleEntity;
import software.wings.search.entities.service.ServiceTimeScaleEntity;
import software.wings.search.entities.tags.TagLinksTimeScaleEntity;
import software.wings.search.entities.user.UserTimeScaleEntity;
import software.wings.search.entities.workflow.WorkflowTimeScaleEntity;
import software.wings.search.framework.ExecutionEntity;
import software.wings.search.framework.TimeScaleEntity;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimescaleModule extends AbstractModule {
  @Override
  protected void configure() {
    bindTimeScaleEntities();
    bindExecutionEntities();
  }

  private void bindTimeScaleEntities() {
    Multibinder<TimeScaleEntity<?>> timeScaleEntityMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<TimeScaleEntity<?>>() {});
    timeScaleEntityMultibinder.addBinding().to(ApplicationTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(TagLinksTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(ServiceTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(WorkflowTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(PipelineTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(EnvironmentTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(CloudProviderTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(InfrastructureDefinitionTimeScaleEntity.class);
    timeScaleEntityMultibinder.addBinding().to(UserTimeScaleEntity.class);
  }

  private void bindExecutionEntities() {
    Multibinder<ExecutionEntity<?>> timeScaleEntityMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<ExecutionEntity<?>>() {});
    timeScaleEntityMultibinder.addBinding().to(DeploymentExecutionEntity.class);
    timeScaleEntityMultibinder.addBinding().to(DeploymentStepExecutionEntity.class);
    timeScaleEntityMultibinder.addBinding().to(ExecutionInterruptEntity.class);
  }
}
