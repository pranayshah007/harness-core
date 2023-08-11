/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.SSCAManagerConfiguration.getResourceClasses;
import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.collect.ImmutableMap.of;

import io.harness.annotations.SSCAAuthIfHasApiKey;
import io.harness.annotations.SSCAServiceAuth;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.govern.ProviderModule;
import io.harness.maintenance.MaintenanceController;
import io.harness.persistence.HPersistence;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.token.remote.TokenClient;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(SSCA)
public class SSCAManagerApplication extends Application<SSCAManagerConfiguration> {
  private static final String APPLICATION_NAME = "SSCA Manager Application";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new SSCAManagerApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<SSCAManagerConfiguration> bootstrap) {
    initializeLogging();

    // bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addBundle(new SwaggerBundle<SSCAManagerConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          SSCAManagerConfiguration sscaManagerConfiguration) {
        return sscaManagerConfiguration.getSwaggerBundleConfiguration();
      }
    });
  }

  @Override
  public void run(SSCAManagerConfiguration sscaManagerConfiguration, Environment environment) throws Exception {
    log.info("Starting SSCA Manager Application ...");
    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SSCAManagerConfiguration configuration() {
        return sscaManagerConfiguration;
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return sscaManagerConfiguration.getDbAliases();
      }
    });

    modules.add(io.harness.SSCAManagerModule.getInstance(sscaManagerConfiguration));
    MaintenanceController.forceMaintenance(true);
    Injector injector = Guice.createInjector(modules);
    injector.getInstance(HPersistence.class);
    registerJerseyProviders(environment, injector);
    registerResources(environment, injector);
    registerOasResource(sscaManagerConfiguration, environment, injector);
    registerAuthFilters(sscaManagerConfiguration, environment, injector);
    registerCorsFilter(sscaManagerConfiguration, environment);
    MaintenanceController.forceMaintenance(false);
  }

  private void registerOasResource(
      SSCAManagerConfiguration sscaManagerConfiguration, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(sscaManagerConfiguration.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : getResourceClasses()) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
  }

  private void registerCorsFilter(SSCAManagerConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(JsonProcessingExceptionMapper.class);
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerAuthFilters(SSCAManagerConfiguration config, Environment environment, Injector injector) {
    if (config.isAuthEnabled()) {
      Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
          -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
                 && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(SSCAServiceAuth.class) != null)
          || (resourceInfoAndRequest.getKey().getResourceClass() != null
              && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(SSCAServiceAuth.class) != null)
          || (resourceInfoAndRequest.getKey().getResourceMethod() != null
              && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(SSCAAuthIfHasApiKey.class) != null
              && resourceInfoAndRequest.getValue().getHeaders().get(X_API_KEY) != null)
          || (resourceInfoAndRequest.getKey().getResourceMethod() != null
              && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null)
          || (resourceInfoAndRequest.getKey().getResourceClass() != null
              && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null);
      Map<String, String> serviceToSecretMapping = new HashMap<>();
      serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), config.getJwtAuthSecret());
      serviceToSecretMapping.put(
          AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), config.getJwtIdentityServiceSecret());
      serviceToSecretMapping.put(AuthorizationServiceHeader.DEFAULT.getServiceId(), config.getNgManagerServiceSecret());
      environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
          injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
      registerInternalApiAuthFilter(config, environment);
    }
  }

  private void registerInternalApiAuthFilter(SSCAManagerConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.DEFAULT.getServiceId(), configuration.getSscaManagerServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }
}
