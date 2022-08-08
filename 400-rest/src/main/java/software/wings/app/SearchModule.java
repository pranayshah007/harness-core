/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.changestreams.ChangeEventFactory;
import io.harness.mongo.changestreams.ChangeStreamModule;
import io.harness.mongo.changestreams.ChangeTracker;

import software.wings.search.ElasticsearchServiceImpl;
import software.wings.search.SearchService;
import software.wings.search.entities.application.ApplicationSearchEntity;
import software.wings.search.entities.application.ApplicationTimeScaleEntity;
import software.wings.search.entities.cloudprovider.CloudProviderTimeScaleEntity;
import software.wings.search.entities.deployment.DeploymentSearchEntity;
import software.wings.search.entities.environment.EnvironmentSearchEntity;
import software.wings.search.entities.environment.EnvironmentTimeScaleEntity;
import software.wings.search.entities.pipeline.PipelineSearchEntity;
import software.wings.search.entities.pipeline.PipelineTimeScaleEntity;
import software.wings.search.entities.service.ServiceSearchEntity;
import software.wings.search.entities.service.ServiceTimeScaleEntity;
import software.wings.search.entities.tags.TagLinksTimeScaleEntity;
import software.wings.search.entities.user.UserTimeScaleEntity;
import software.wings.search.entities.workflow.WorkflowSearchEntity;
import software.wings.search.entities.workflow.WorkflowTimeScaleEntity;
import software.wings.search.framework.SearchDao;
import software.wings.search.framework.SearchEntity;
import software.wings.search.framework.SynchronousElasticsearchDao;
import software.wings.search.framework.TimeScaleEntity;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * Module with Binding for all search related classes.
 *
 * @author utkarsh
 */

@OwnedBy(PL)
@Slf4j
public class SearchModule extends AbstractModule {
  @Provides
  @Singleton
  public RestHighLevelClient getClient(MainConfiguration mainConfiguration) {
    try {
      URI uri = new URIBuilder(mainConfiguration.getElasticsearchConfig().getUri()).build();
      return new RestHighLevelClient(RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())));
    } catch (URISyntaxException e) {
      log.error(
          String.format("Elasticsearch URI %s is invalid", mainConfiguration.getElasticsearchConfig().getUri()), e);
    }
    return null;
  }

  @Override
  protected void configure() {
    install(ChangeStreamModule.getInstance());
    bind(SearchService.class).to(ElasticsearchServiceImpl.class);
    bind(SearchDao.class).to(SynchronousElasticsearchDao.class);
    bindEntities();
    bindTimeScaleEntities();
  }

  private void bindEntities() {
    Multibinder<SearchEntity<?>> searchEntityMultibinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<SearchEntity<?>>() {});
    searchEntityMultibinder.addBinding().to(ApplicationSearchEntity.class);
    searchEntityMultibinder.addBinding().to(PipelineSearchEntity.class);
    searchEntityMultibinder.addBinding().to(WorkflowSearchEntity.class);
    searchEntityMultibinder.addBinding().to(ServiceSearchEntity.class);
    searchEntityMultibinder.addBinding().to(EnvironmentSearchEntity.class);
    searchEntityMultibinder.addBinding().to(DeploymentSearchEntity.class);
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
    timeScaleEntityMultibinder.addBinding().to(UserTimeScaleEntity.class);
  }

  @Provides
  @Named("Search")
  public ChangeTracker getChangeTracker(Injector injector) {
    MongoConfig mongoConfig = injector.getInstance(MongoConfig.class);
    ChangeEventFactory changeEventFactory = injector.getInstance(ChangeEventFactory.class);
    MainConfiguration mainConfiguration = injector.getInstance(MainConfiguration.class);
    TagSet tags = null;
    if (!mainConfiguration.getElasticsearchConfig().getMongoTagKey().equals("none")) {
      tags = new TagSet(new Tag(mainConfiguration.getElasticsearchConfig().getMongoTagKey(),
          mainConfiguration.getElasticsearchConfig().getMongoTagValue()));
    }
    return new ChangeTracker(mongoConfig, changeEventFactory, tags);
  }
}
