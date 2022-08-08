/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.datafetcher.apiKey.CreateApiKeyDataFetcher;
import io.harness.app.datafetcher.apiKey.DeleteApiKeyDataFetcher;
import io.harness.app.datafetcher.apiKey.UpdateApiKeyDataFetcher;
import io.harness.app.datafetcher.delegate.AddDelegateScopeDataFetcher;
import io.harness.app.datafetcher.delegate.AttachAllScopesToDelegateDataFetcher;
import io.harness.app.datafetcher.delegate.AttachScopeToDelegateDataFetcher;
import io.harness.app.datafetcher.delegate.DelegateApprovalDataFetcher;
import io.harness.app.datafetcher.delegate.DelegateListDataFetcher;
import io.harness.app.datafetcher.delegate.DelegateScopeListDataFetcher;
import io.harness.app.datafetcher.delegate.DeleteDelegateDataFetcher;
import io.harness.app.datafetcher.template.TemplateListDataFetcher;

import software.wings.app.WingsGraphQLModule;
import software.wings.graphql.datafetcher.approval.ApprovalDetailsDataFetcher;
import software.wings.graphql.datafetcher.approval.ApproveOrRejectApprovalsDataFetcher;
import software.wings.graphql.datafetcher.artifact.ArtifactCleanupDataFetcher;
import software.wings.graphql.datafetcher.artifact.ArtifactConnectionDataFetcher;
import software.wings.graphql.datafetcher.artifact.ArtifactDataFetcher;
import software.wings.graphql.datafetcher.artifact.SetArtifactCollectionEnabledDataFetcher;
import software.wings.graphql.datafetcher.deploymentfreezewindow.CreateDeploymentFreezeWindowDataFetcher;
import software.wings.graphql.datafetcher.deploymentfreezewindow.DeleteDeploymentFreezeWindowDataFetcher;
import software.wings.graphql.datafetcher.deploymentfreezewindow.DeploymentFreezeWindowConnectionDataFetcher;
import software.wings.graphql.datafetcher.deploymentfreezewindow.DeploymentFreezeWindowDataFetcher;
import software.wings.graphql.datafetcher.deploymentfreezewindow.ToggleDeploymentFreezeWindowDataFetcher;
import software.wings.graphql.datafetcher.deploymentfreezewindow.UpdateDeploymentFreezeWindowDataFetcher;
import software.wings.graphql.datafetcher.event.CreateEventsConfigDataFetcher;
import software.wings.graphql.datafetcher.event.DeleteEventsConfigDataFetcher;
import software.wings.graphql.datafetcher.event.EventsConfigConnectionDataFetcher;
import software.wings.graphql.datafetcher.event.EventsConfigDataFetcher;
import software.wings.graphql.datafetcher.event.UpdateEventsConfigDataFetcher;
import software.wings.graphql.datafetcher.execution.DeploymentStatsDataFetcher;
import software.wings.graphql.datafetcher.instance.instanceInfo.InstanceController;
import software.wings.graphql.datafetcher.secretManager.HashicorpVaultDataFetcher;
import software.wings.graphql.datafetcher.secretManager.SecretManagerDataFetchers;
import software.wings.graphql.datafetcher.secretManager.SecretManagerMutationDataFetcher;
import software.wings.graphql.datafetcher.secretmanager.CustomSecretManagerDataFetcher;
import software.wings.graphql.datafetcher.trigger.CreateTriggerDataFetcher;
import software.wings.graphql.datafetcher.trigger.DeleteTriggerDataFetcher;
import software.wings.graphql.datafetcher.trigger.TriggerConnectionDataFetcher;
import software.wings.graphql.datafetcher.trigger.TriggerDataFetcher;
import software.wings.graphql.datafetcher.trigger.TriggerStatsDataFetcher;
import software.wings.graphql.datafetcher.trigger.UpdateTriggerDataFetcher;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import graphql.schema.DataFetcher;
import java.util.Collections;
import java.util.Set;
import org.dataloader.MappedBatchLoader;
import org.hibernate.validator.constraints.NotBlank;
import org.jetbrains.annotations.NotNull;

/**
 * Created a new module as part of code review comment
 */
@OwnedBy(DX)
public class GraphQLModule extends AbstractModule {
  /***
   * This collection is mainly required to inject batched loader at app start time.
   * I was not getting a handle to Annotation Name at runtime hence I am taking this approach.
   */
  private static final Set<String> BATCH_DATA_LOADER_NAMES = Sets.newHashSet();
  private static final String DATA_FETCHER_SUFFIX = "DataFetcher";
  public static final String BATCH_SUFFIX = "Batch";
  private static final String BATCH_DATA_LOADER_SUFFIX = BATCH_SUFFIX.concat("DataLoader");

  private static volatile GraphQLModule instance;

  public static GraphQLModule getInstance() {
    if (instance == null) {
      instance = new GraphQLModule();
    }
    return instance;
  }

  private GraphQLModule() {}

  public static Set<String> getBatchDataLoaderNames() {
    return Collections.unmodifiableSet(BATCH_DATA_LOADER_NAMES);
  }

  @Override
  protected void configure() {
    install(WingsGraphQLModule.getInstance());

    // DATA FETCHERS ARE NOT SINGLETON AS THEY CAN HAVE DIFFERENT CONTEXT MAP
    bindDataFetchers();

    bindBatchedDataLoaderWithAnnotation();

    bindInstanceInfoControllers();
  }

  private void bindBatchedDataLoaderWithAnnotation() {}

  private void bindInstanceInfoControllers() {
    MapBinder<Class, InstanceController> instanceInfoControllerMapBinder =
        MapBinder.newMapBinder(binder(), Class.class, InstanceController.class);
  }

  private void bindDataFetchers() {
    bindDataFetcherWithAnnotation(TriggerConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(TriggerStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(TriggerDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateTriggerDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateTriggerDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteTriggerDataFetcher.class);
    bindDataFetcherWithAnnotation(ArtifactDataFetcher.class);
    bindDataFetcherWithAnnotation(ArtifactConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(ArtifactCleanupDataFetcher.class);
    bindDataFetcherWithAnnotation(DelegateApprovalDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteDelegateDataFetcher.class);
    bindDataFetcherWithAnnotation(DelegateListDataFetcher.class);
    bindDataFetcherWithAnnotation(DelegateScopeListDataFetcher.class);
    bindDataFetcherWithAnnotation(AddDelegateScopeDataFetcher.class);
    bindDataFetcherWithAnnotation(AttachScopeToDelegateDataFetcher.class);
    bindDataFetcherWithAnnotation(AttachAllScopesToDelegateDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateEventsConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteEventsConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(EventsConfigConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(EventsConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateEventsConfigDataFetcher.class);
    bindDataFetcherWithAnnotation(ApprovalDetailsDataFetcher.class);
    bindDataFetcherWithAnnotation(DeploymentStatsDataFetcher.class);
    bindDataFetcherWithAnnotation(ApproveOrRejectApprovalsDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateDeploymentFreezeWindowDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateDeploymentFreezeWindowDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteDeploymentFreezeWindowDataFetcher.class);
    bindDataFetcherWithAnnotation(DeploymentFreezeWindowConnectionDataFetcher.class);
    bindDataFetcherWithAnnotation(DeploymentFreezeWindowDataFetcher.class);
    bindDataFetcherWithAnnotation(ToggleDeploymentFreezeWindowDataFetcher.class);
    bindDataFetcherWithAnnotation(TemplateListDataFetcher.class);
    bindDataFetcherWithAnnotation(CreateApiKeyDataFetcher.class);
    bindDataFetcherWithAnnotation(UpdateApiKeyDataFetcher.class);
    bindDataFetcherWithAnnotation(DeleteApiKeyDataFetcher.class);
    binder()
        .bind(SecretManagerMutationDataFetcher.class)
        .annotatedWith(Names.named(SecretManagerDataFetchers.HASHICORP_VAULT_DATA_FETCHER.getName()))
        .to(HashicorpVaultDataFetcher.class);
    binder()
        .bind(SecretManagerMutationDataFetcher.class)
        .annotatedWith(Names.named(SecretManagerDataFetchers.CUSTOM_SECRET_MANAGER_DATA_FETCHER.getName()))
        .to(CustomSecretManagerDataFetcher.class);
    bindDataFetcherWithAnnotation(SetArtifactCollectionEnabledDataFetcher.class);
  }

  @NotNull
  public static String calculateAnnotationName(final Class clazz, String suffixToRemove) {
    String className = clazz.getName();
    char c[] = className.substring(className.lastIndexOf('.') + 1, clazz.getName().length() - suffixToRemove.length())
                   .toCharArray();

    c[0] = Character.toLowerCase(c[0]);
    return new String(c);
  }

  private void bindDataFetcherWithAnnotation(Class<? extends DataFetcher> clazz, String suffix) {
    String annotationName = calculateAnnotationName(clazz, suffix);
    bind(DataFetcher.class).annotatedWith(Names.named(annotationName)).to(clazz);
  }

  private void bindDataFetcherWithAnnotation(Class<? extends DataFetcher> clazz) {
    bindDataFetcherWithAnnotation(clazz, DATA_FETCHER_SUFFIX);
  }

  private void bindBatchedDataLoaderWithAnnotation(Class<? extends MappedBatchLoader> clazz) {
    String annotationName = calculateAnnotationName(clazz, BATCH_DATA_LOADER_SUFFIX);
    BATCH_DATA_LOADER_NAMES.add(annotationName);
    bind(MappedBatchLoader.class).annotatedWith(Names.named(annotationName)).to(clazz).in(Scopes.SINGLETON);
  }

  public static String getBatchDataLoaderAnnotationName(@NotBlank String dataFetcherName) {
    return dataFetcherName.concat(BATCH_SUFFIX);
  }
}
