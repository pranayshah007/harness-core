/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.modules;

import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.impl.AzureAuthorizationClientImpl;
import io.harness.azure.impl.AzureComputeClientImpl;
import io.harness.azure.impl.AzureContainerRegistryClientImpl;
import io.harness.azure.impl.AzureKubernetesClientImpl;
import io.harness.azure.impl.AzureManagementClientImpl;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.exceptionhandler.handler.AmazonClientExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AmazonServiceExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.AuthenticationExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.CVConnectorExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.DockerServerExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.GcpClientExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.HashicorpVaultExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.HelmClientRuntimeExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.InterruptedIOExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.SCMExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.SecretExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.SocketExceptionHandler;
import io.harness.delegate.exceptionhandler.handler.TerraformRuntimeExceptionHandler;
import io.harness.delegate.k8s.K8sApplyRequestHandler;
import io.harness.delegate.k8s.K8sRequestHandler;
import io.harness.delegate.k8s.K8sRollingRequestHandler;
import io.harness.delegate.service.K8sGlobalConfigServiceImpl;
import io.harness.delegate.task.azure.exception.AzureARMRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureAppServicesRuntimeExceptionHandler;
import io.harness.delegate.task.azure.exception.AzureClientExceptionHandler;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.exception.KubernetesApiClientRuntimeExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesApiExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesCliRuntimeExceptionHandler;
import io.harness.delegate.task.scm.ScmDelegateClientImpl;
import io.harness.delegate.utils.DecryptionHelperDelegate;
import io.harness.exception.ExplanationException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.git.GitClientV2;
import io.harness.git.GitClientV2Impl;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesContainerServiceImpl;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.kustomize.KustomizeClient;
import io.harness.kustomize.KustomizeClientImpl;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestServiceImpl;
import io.harness.openshift.OpenShiftClient;
import io.harness.openshift.OpenShiftClientImpl;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;
import io.harness.threading.ThreadPool;

import software.wings.service.impl.security.EncryptionServiceImpl;
import software.wings.service.impl.security.SecretDecryptionServiceImpl;
import software.wings.service.intfc.security.EncryptionService;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KubernetesNgTasksModule extends AbstractModule {
  @Provides
  @Singleton
  @Named("asyncExecutor")
  public ExecutorService asyncExecutor() {
    return ThreadPool.create(10, 400, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("async-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  @Provides
  @Singleton
  @Named("k8sSteadyStateExecutor")
  public ExecutorService k8sSteadyStateExecutor() {
    return Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("k8sSteadyState-%d").setPriority(Thread.MAX_PRIORITY).build());
  }

  @Override
  protected void configure() {
    bindRequestHandlers();
    registerK8sNgBindings();
    bindExceptionHandlers();
  }

  private void bindRequestHandlers() {
    // NG Delegate
    MapBinder<String, K8sRequestHandler> k8sTaskTypeToRequestHandler =
        MapBinder.newMapBinder(binder(), String.class, K8sRequestHandler.class);
    k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.APPLY.name()).to(K8sApplyRequestHandler.class);
    k8sTaskTypeToRequestHandler.addBinding(K8sTaskType.DEPLOYMENT_ROLLING.name()).to(K8sRollingRequestHandler.class);
  }

  private void registerK8sNgBindings() {
    bind(EncryptionService.class).to(EncryptionServiceImpl.class);
    bind(SecretDecryptionService.class).to(SecretDecryptionServiceImpl.class);
    bind(DecryptionHelper.class).to(DecryptionHelperDelegate.class);

    bind(K8sGlobalConfigService.class).to(K8sGlobalConfigServiceImpl.class);

    bind(KubernetesContainerService.class).to(KubernetesContainerServiceImpl.class);
    bind(Clock.class).toInstance(Clock.systemUTC());

    bind(AzureAuthorizationClient.class).to(AzureAuthorizationClientImpl.class);
    bind(AzureComputeClient.class).to(AzureComputeClientImpl.class);
    bind(AzureContainerRegistryClient.class).to(AzureContainerRegistryClientImpl.class);
    bind(AzureKubernetesClient.class).to(AzureKubernetesClientImpl.class);
    bind(AzureManagementClient.class).to(AzureManagementClientImpl.class);
    try {
      bind(new TypeLiteral<DataStore<StoredCredential>>() {
      }).toInstance(StoredCredential.getDefaultDataStore(new MemoryDataStoreFactory()));
    } catch (IOException e) {
      String msg =
          "Could not initialise GKE access token memory cache. This should not never happen with memory data store.";
      throw new ExplanationException(msg, e);
    }

    bind(NGGitService.class).to(NGGitServiceImpl.class);
    bind(GitClientV2.class).to(GitClientV2Impl.class).asEagerSingleton();
    bind(KustomizeClient.class).to(KustomizeClientImpl.class);

    bind(OpenShiftClient.class).to(OpenShiftClientImpl.class);
    bind(ScmDelegateClient.class).to(ScmDelegateClientImpl.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(CustomManifestService.class).to(CustomManifestServiceImpl.class);
  }

  private void bindExceptionHandlers() {
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});

    AmazonServiceExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AmazonServiceExceptionHandler.class));
    AmazonClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AmazonClientExceptionHandler.class));
    GcpClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(GcpClientExceptionHandler.class));
    HashicorpVaultExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(HashicorpVaultExceptionHandler.class));
    DockerServerExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(DockerServerExceptionHandler.class));
    SecretExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(SecretExceptionHandler.class));
    SocketExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(SocketExceptionHandler.class));
    InterruptedIOExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(InterruptedIOExceptionHandler.class));
    CVConnectorExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(CVConnectorExceptionHandler.class));
    SCMExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(SCMExceptionHandler.class));
    AuthenticationExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AuthenticationExceptionHandler.class));
    HelmClientRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(HelmClientRuntimeExceptionHandler.class));
    KubernetesApiExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(KubernetesApiExceptionHandler.class));
    KubernetesApiClientRuntimeExceptionHandler.exceptions().forEach(exception
        -> exceptionHandlerMapBinder.addBinding(exception).to(KubernetesApiClientRuntimeExceptionHandler.class));
    TerraformRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(TerraformRuntimeExceptionHandler.class));
    KubernetesCliRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(KubernetesCliRuntimeExceptionHandler.class));
    AzureAppServicesRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureAppServicesRuntimeExceptionHandler.class));
    AzureClientExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureClientExceptionHandler.class));
    AzureARMRuntimeExceptionHandler.exceptions().forEach(
        exception -> exceptionHandlerMapBinder.addBinding(exception).to(AzureARMRuntimeExceptionHandler.class));
  }
}
