/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci;

import io.harness.CIBeansModule;
import io.harness.OrchestrationModule;
import io.harness.OrchestrationModuleConfig;
import io.harness.OrchestrationStepsModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.nodes.ArtifactoryUploadNode;
import io.harness.beans.steps.nodes.BuildAndPushDockerNode;
import io.harness.beans.steps.nodes.BuildAndPushECRNode;
import io.harness.beans.steps.nodes.BuildAndPushGCRNode;
import io.harness.beans.steps.nodes.GCSUploadNode;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.RestoreCacheGCSNode;
import io.harness.beans.steps.nodes.RestoreCacheS3Node;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.S3UploadNode;
import io.harness.beans.steps.nodes.SaveCacheGCSNode;
import io.harness.beans.steps.nodes.SaveCacheS3Node;
import io.harness.beans.steps.nodes.SecurityNode;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.InitializeStepInfoBuilder;
import io.harness.ci.integrationstage.K8InitializeStepInfoBuilder;
import io.harness.ci.serializer.PluginCompatibleStepSerializer;
import io.harness.ci.serializer.PluginStepProtobufSerializer;
import io.harness.ci.serializer.ProtobufStepSerializer;
import io.harness.ci.serializer.RunStepProtobufSerializer;
import io.harness.ci.serializer.RunTestsStepProtobufSerializer;
import io.harness.engine.expressions.AmbianceExpressionEvaluatorProvider;
import io.harness.pms.listener.NgOrchestrationNotifyEventListener;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@OwnedBy(HarnessTeam.CI)
public class CIExecutionServiceModule extends AbstractModule {
  private CIExecutionServiceConfig ciExecutionServiceConfig;
  private final Boolean withPMS;

  public static Set<Class<?>> ciStepsMovedToNewSchema = new HashSet() {
    {
      add(RunStepNode.class);
      add(RunTestStepNode.class);
      add(GCSUploadNode.class);
      add(S3UploadNode.class);
      add(BuildAndPushDockerNode.class);
      add(BuildAndPushECRNode.class);
      add(BuildAndPushGCRNode.class);
      add(SaveCacheS3Node.class);
      add(SaveCacheGCSNode.class);
      add(RestoreCacheGCSNode.class);
      add(RestoreCacheS3Node.class);
      add(PluginStepNode.class);
      add(SecurityNode.class);
      add(ArtifactoryUploadNode.class);
    }
  };
  @Inject
  public CIExecutionServiceModule(CIExecutionServiceConfig ciExecutionServiceConfig, Boolean withPMS) {
    this.ciExecutionServiceConfig = ciExecutionServiceConfig;
    this.withPMS = withPMS;
  }

  @Override
  protected void configure() {
    install(CIBeansModule.getInstance());
    install(OrchestrationStepsModule.getInstance(null));
    install(OrchestrationModule.getInstance(OrchestrationModuleConfig.builder()
                                                .serviceName("CI")
                                                .withPMS(Optional.ofNullable(withPMS).orElse(false))
                                                .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
                                                .publisherName(NgOrchestrationNotifyEventListener.NG_ORCHESTRATION)
                                                .build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("ciEventHandlerExecutor"))
        .toInstance(ThreadPool.create(
            20, 300, 5, TimeUnit.SECONDS, new ThreadFactoryBuilder().setNameFormat("Event-Handler-%d").build()));
    this.bind(CIExecutionServiceConfig.class).toInstance(this.ciExecutionServiceConfig);
    bind(InitializeStepInfoBuilder.class).to(K8InitializeStepInfoBuilder.class);
    bind(new TypeLiteral<ProtobufStepSerializer<RunStepInfo>>() {}).toInstance(new RunStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<PluginStepInfo>>() {}).toInstance(new PluginStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<RunTestsStepInfo>>() {
    }).toInstance(new RunTestsStepProtobufSerializer());
    bind(new TypeLiteral<ProtobufStepSerializer<PluginCompatibleStep>>() {
    }).toInstance(new PluginCompatibleStepSerializer());
  }
}
