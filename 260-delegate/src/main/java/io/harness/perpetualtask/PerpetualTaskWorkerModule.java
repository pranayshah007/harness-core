/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.perpetualtask.PerpetualTaskServiceGrpc.PerpetualTaskServiceBlockingStub;
import io.harness.perpetualtask.artifact.ArtifactCollectionTaskParams;
import io.harness.perpetualtask.artifact.ArtifactPerpetualTaskExecutor;
import io.harness.perpetualtask.connector.ConnectorHeartbeatPerpetualTaskExecutor;
import io.harness.perpetualtask.connector.ConnectorHeartbeatTaskParams;
import io.harness.perpetualtask.datacollection.DataCollectionPerpetualTaskExecutor;
import io.harness.perpetualtask.datacollection.DataCollectionPerpetualTaskParams;
import io.harness.perpetualtask.datacollection.K8ActivityCollectionPerpetualTaskExecutor;
import io.harness.perpetualtask.datacollection.K8ActivityCollectionPerpetualTaskParams;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskExecutor;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskParams;
import io.harness.perpetualtask.example.SamplePerpetualTaskExecutor;
import io.harness.perpetualtask.example.SamplePerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AsgInstanceSyncPerpetualTaskParamsNg;
import io.harness.perpetualtask.instancesync.AwsAmiInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AwsCodeDeployInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskParamsNg;
import io.harness.perpetualtask.instancesync.AwsSamInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.perpetualtask.instancesync.AzureSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.perpetualtask.instancesync.AzureVmssInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.AzureWebAppInstanceSyncPerpetualProtoTaskParams;
import io.harness.perpetualtask.instancesync.AzureWebAppNGInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.ContainerInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.CustomDeploymentInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.EcsInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.GoogleFunctionInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.K8sInstanceSyncPerpetualTaskParamsV2;
import io.harness.perpetualtask.instancesync.NativeHelmInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.PcfInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.PdcInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.PdcPerpetualTaskParamsNg;
import io.harness.perpetualtask.instancesync.ServerlessAwsLambdaInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskParamsNg;
import io.harness.perpetualtask.instancesync.TasInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.cg.CgInstanceSyncV2TaskExecutor;
import io.harness.perpetualtask.k8s.watch.K8SWatchTaskExecutor;
import io.harness.perpetualtask.k8s.watch.K8sWatchTaskParams;
import io.harness.perpetualtask.k8s.watch.NodeWatcher;
import io.harness.perpetualtask.k8s.watch.PVWatcher;
import io.harness.perpetualtask.k8s.watch.PodWatcher;
import io.harness.perpetualtask.k8s.watch.WatcherFactory;
import io.harness.perpetualtask.manifest.ManifestCollectionTaskParams;
import io.harness.perpetualtask.manifest.ManifestPerpetualTaskExecutor;
import io.harness.perpetualtask.polling.ArtifactCollectionTaskParamsNg;
import io.harness.perpetualtask.polling.GitPollingTaskParamsNg;
import io.harness.perpetualtask.polling.ManifestCollectionTaskParamsNg;
import io.harness.perpetualtask.polling.artifact.ArtifactPerpetualTaskExecutorNg;
import io.harness.perpetualtask.polling.gitpolling.GitPollingPerpetualTaskExecutorNg;
import io.harness.perpetualtask.polling.manifest.ManifestPerpetualTaskExecutorNg;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import io.grpc.CallCredentials;
import io.grpc.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class PerpetualTaskWorkerModule extends AbstractModule {
  @Override
  protected void configure() {
    MapBinder<String, PerpetualTaskExecutor> mapBinder =
        MapBinder.newMapBinder(binder(), String.class, PerpetualTaskExecutor.class);
    mapBinder.addBinding(SamplePerpetualTaskParams.class.getSimpleName()).to(SamplePerpetualTaskExecutor.class);
    mapBinder.addBinding(K8sWatchTaskParams.class.getSimpleName()).to(K8SWatchTaskExecutor.class);
    mapBinder.addBinding(EcsPerpetualTaskParams.class.getSimpleName()).to(EcsPerpetualTaskExecutor.class);
    mapBinder.addBinding(ArtifactCollectionTaskParams.class.getSimpleName()).to(ArtifactPerpetualTaskExecutor.class);
    mapBinder.addBinding(PcfInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(PcfInstanceSyncDelegateExecutor.class);
    mapBinder.addBinding(AwsAmiInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AwsAmiInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(AwsSshInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AwsSshInstanceSyncExecutor.class);
    mapBinder.addBinding(PdcInstanceSyncPerpetualTaskParams.class.getSimpleName()).to(PdcInstanceSyncExecutor.class);
    mapBinder.addBinding(AwsLambdaInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AwsLambdaInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(SpotinstAmiInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(SpotinstAmiInstanceSyncDelegateExecutor.class);
    mapBinder.addBinding(AzureVmssInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AzureVMSSInstanceSyncDelegateExecutor.class);
    mapBinder.addBinding(AzureWebAppInstanceSyncPerpetualProtoTaskParams.class.getSimpleName())
        .to(AzureWebAppInstanceSyncDelegateExecutor.class);
    mapBinder.addBinding(AwsCodeDeployInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AwsCodeDeployInstanceSyncExecutor.class);
    mapBinder.addBinding(ContainerInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(ContainerInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(DataCollectionPerpetualTaskParams.class.getSimpleName())
        .to(DataCollectionPerpetualTaskExecutor.class);
    mapBinder.addBinding(K8ActivityCollectionPerpetualTaskParams.class.getSimpleName())
        .to(K8ActivityCollectionPerpetualTaskExecutor.class);
    mapBinder.addBinding(CustomDeploymentInstanceSyncTaskParams.class.getSimpleName())
        .to(CustomDeploymentPerpetualTaskExecutor.class);
    mapBinder.addBinding(ManifestCollectionTaskParams.class.getSimpleName()).to(ManifestPerpetualTaskExecutor.class);
    mapBinder.addBinding(ConnectorHeartbeatTaskParams.class.getSimpleName())
        .to(ConnectorHeartbeatPerpetualTaskExecutor.class);
    mapBinder.addBinding(CgInstanceSyncTaskParams.class.getSimpleName()).to(CgInstanceSyncV2TaskExecutor.class);

    // NG
    mapBinder.addBinding(NativeHelmInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(NativeHelmInstanceSyncPerpetualTaskExcecutor.class);
    mapBinder.addBinding(K8sInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(K8sInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(ManifestCollectionTaskParamsNg.class.getSimpleName())
        .to(ManifestPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(ArtifactCollectionTaskParamsNg.class.getSimpleName())
        .to(ArtifactPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(GitPollingTaskParamsNg.class.getSimpleName()).to(GitPollingPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(ServerlessAwsLambdaInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(ServerlessAwsLambdaInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(AzureWebAppNGInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AzureWebAppInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(CustomDeploymentNGInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(CustomDeploymentInstanceSyncPerpetualTaskExecuter.class);
    mapBinder.addBinding(TasInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(TasInstanceSyncPerpetualTaskExecuter.class);
    mapBinder.addBinding(EcsInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(EcsInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(PdcPerpetualTaskParamsNg.class.getSimpleName()).to(PdcPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(AzureSshInstanceSyncPerpetualTaskParamsNg.class.getSimpleName())
        .to(AzureSshWinrmInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(AwsSshInstanceSyncPerpetualTaskParamsNg.class.getSimpleName())
        .to(AwsSshWinrmPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(SpotinstAmiInstanceSyncPerpetualTaskParamsNg.class.getSimpleName())
        .to(SpotinstPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(AsgInstanceSyncPerpetualTaskParamsNg.class.getSimpleName())
        .to(AsgInstanceSyncPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(GoogleFunctionInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(GoogleFunctionInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(AwsSamInstanceSyncPerpetualTaskParams.class.getSimpleName())
        .to(AwsSamInstanceSyncPerpetualTaskExecutor.class);
    mapBinder.addBinding(AwsLambdaInstanceSyncPerpetualTaskParamsNg.class.getSimpleName())
        .to(AwsLambdaInstanceSyncPerpetualTaskExecutorNg.class);
    mapBinder.addBinding(K8sInstanceSyncPerpetualTaskParamsV2.class.getSimpleName())
        .to(K8sInstanceSyncPerpetualTaskV2Executor.class);

    install(new FactoryModuleBuilder()
                .implement(PodWatcher.class, PodWatcher.class)
                .implement(NodeWatcher.class, NodeWatcher.class)
                .implement(PVWatcher.class, PVWatcher.class)
                .build(WatcherFactory.class));
  }

  @Provides
  @Singleton
  PerpetualTaskServiceBlockingStub perpetualTaskServiceBlockingStub(
      @Named("manager-channel") Channel channel, CallCredentials callCredentials) {
    return PerpetualTaskServiceGrpc.newBlockingStub(channel).withCallCredentials(callCredentials);
  }
}
