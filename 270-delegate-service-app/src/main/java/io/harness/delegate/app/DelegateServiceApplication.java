/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.dropwizard.auth.AuthValueFactoryProvider;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.StartupMode;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import software.wings.app.InspectCommand;
import software.wings.app.MainConfiguration.AssetsConfigurationMixin;
import software.wings.app.WingsApplication;
import software.wings.beans.User;
import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.bundles.assets.AssetsConfiguration;
import io.dropwizard.bundles.assets.ConfiguredAssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import software.wings.security.AuthResponseFilter;
import software.wings.security.AuthRuleFilter;
import software.wings.security.AuthenticationFilter;
import software.wings.security.LoginRateLimitFilter;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
public class DelegateServiceApplication extends Application<DelegateServiceConfig> {
  public static void main(String... args) throws Exception {
    new DelegateServiceApplication().run(args);
  }

  @Override
  public void run(DelegateServiceConfig delegateServiceConfig, Environment environment) throws Exception {
    log.info("Starting Delegate Service App");
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        20, 1000, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    modules.add(new DelegateServiceModule(delegateServiceConfig));

    Injector injector = Guice.createInjector(modules);
    initializeDelegateSvc(injector, environment, delegateServiceConfig);
    log.info("Starting Delegate Service App done");
  }

  private void initializeDelegateSvc(Injector injector, Environment environment, DelegateServiceConfig delegateServiceConfig) {
    // copied below from WingsApplication corresponding to dms flag, will uncomment as applicable
//    registerAtmosphereStreams(environment, injector);
//    initializeFeatureFlags(configuration, injector);
//    registerHealthChecksDelegateService(environment, injector);
//    registerStores(configuration, injector);
//    registerDataStores(injector);
//    registerResources(configuration, environment, injector);
//    registerInprocPerpetualTaskServiceClients(injector);
//    registerCorsFilter(configuration, environment);
//    registerAuditResponseFilter(environment, injector);
//    registerJerseyProviders(environment, injector);
//    registerCharsetResponseFilter(environment, injector);
    // Authentication/Authorization filters
    registerAuthFilters(delegateServiceConfig, environment, injector);
//    registerCorrelationFilter(environment, injector);
//    registerRequestContextFilter(environment);
  }

  private void registerAuthFilters(DelegateServiceConfig delegateServiceConfig, Environment environment, Injector injector) {
    // commented changes which I think are not required as now.
    environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
    //environment.jersey().register(injector.getInstance(LoginRateLimitFilter.class));
    environment.jersey().register(injector.getInstance(AuthRuleFilter.class));
    //environment.jersey().register(injector.getInstance(AuthResponseFilter.class));
    environment.jersey().register(injector.getInstance(AuthenticationFilter.class));
  }

  @Override
  public String getName() {
    return "Delegate Service Application";
  }

  @Override
  public void initialize(Bootstrap<DelegateServiceConfig> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    bootstrap.addCommand(new InspectCommand<>(this));

    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new ConfiguredAssetsBundle("/static", "/", "index.html"));
    bootstrap.addBundle(new SwaggerBundle<DelegateServiceConfig>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(DelegateServiceConfig delegateServiceConfig) {
        return delegateServiceConfig.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.addBundle(new FileAssetsBundle("/.well-known"));
    //    ObjectMapper mapper = Jackson.newObjectMapper(new YAMLFactory());
    //    bootstrap.setObjectMapper(mapper);
    configureObjectMapper(bootstrap.getObjectMapper());
    //    this.bootstrap = bootstrap;
    //    bootstrap.setMetricRegistry(metricRegistry);

    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.addMixIn(AssetsConfiguration.class, AssetsConfigurationMixin.class);
    final AnnotationAwareJsonSubtypeResolver subtypeResolver =
        AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver());
    mapper.setSubtypeResolver(subtypeResolver);
    mapper.setConfig(mapper.getSerializationConfig().withView(JsonViews.Public.class));
    mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      // defining a different serialVersionUID then base. We don't care about serializing it.
      private static final long serialVersionUID = 7777451630128399020L;
      @Override
      public List<NamedType> findSubtypes(Annotated a) {
        final List<NamedType> subtypesFromSuper = super.findSubtypes(a);
        if (isNotEmpty(subtypesFromSuper)) {
          return subtypesFromSuper;
        }
        return emptyIfNull(subtypeResolver.findSubtypes(a));
      }
    });
  }

  private void initializegRPCServer(Injector injector) {
    log.info("Initializing gRPC Server on Delegate Service application");
    injector.getInstance(ServiceManager.class);
    ServiceManager serviceManager = injector.getInstance(ServiceManager.class).startAsync();
    serviceManager.awaitHealthy();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceManager.stopAsync().awaitStopped()));
  }
}
