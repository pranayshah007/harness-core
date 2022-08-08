/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.collect.ImmutableMap.of;

import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.notification.module.NotificationClientApplicationModule;
import io.harness.notification.notificationclient.NotificationClientImpl;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.queue.QueueListenerController;
import io.harness.remote.CharsetResponseFilter;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.serializer.HObjectMapper;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@Slf4j
public class NotificationClientApplication extends Application<NotificationClientApplicationConfiguration> {
  private static final String APPLICATION_NAME = "Notification API Client Test";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new NotificationClientApplication().run(args);
  }

  private final MetricRegistry metricRegistry = new MetricRegistry();

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<NotificationClientApplicationConfiguration> bootstrap) {
    initializeLogging();
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
  }
  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
  }

  @Override
  public void run(NotificationClientApplicationConfiguration appConfig, Environment environment) {
    log.info("Starting Next Gen Application ...");
    Injector injector = Guice.createInjector(
        new NotificationClientApplicationModule(appConfig), new MetricRegistryModule(metricRegistry));
    NotificationClientImpl notificationClient = injector.getInstance(NotificationClientImpl.class);
    notificationClient.saveNotificationTemplate(Team.CV, PredefinedTemplate.PD_VANILLA, true);
    //    notificationClient.sendNotificationAsync(EmailChannel.builder()
    //                                                 .accountId("kmpySmUISimoRrJL6NL73w")
    //                                                 .recipients(Collections.singletonList("ankush.shaw@harness.io"))
    //                                                 .team(Team.CD)
    //                                                 .templateId("email_test")
    //                                                 .templateData(Collections.emptyMap())
    //                                                 .userGroupIds(Collections.emptyList())
    //                                                 .build());
    //

    //    notificationClient.sendNotificationAsync(
    //            SlackChannel.builder()
    //                    .accountId("kmpySmUISimoRrJL6NL73w")
    //                    .webhookUrls(Collections.emptyList())
    //                    .team(Team.CV)
    //                    .templateId("slack_vanilla")
    //                    .templateData(Collections.singletonMap("message", "test"))
    //                    .userGroupIds(Collections.emptyList())
    //                    .build());

    //    NotificationResult result = notificationClient.sendNotificationAsync(
    //        PagerDutyChannel.builder()
    //            .accountId("kmpySmUISimoRrJL6NL73w")
    //            .integrationKeys(Collections.emptyList())
    //            .team(Team.CD)
    //            .templateId("pd_vanilla")
    //            .templateData(ImmutableMap.of(
    //                "message", "this is test plain message", "link_href", "www.google.com", "link_text", "Google"))
    //            .userGroupIds(Collections.emptyList())
    //            .build());
    //    log.info("Result {}", result.getNotificationId());

    //    notificationClient.sendNotificationAsync(MSTeamChannel.builder()
    //                                                 .accountId("kmpySmUISimoRrJL6NL73w")
    //                                                 .msTeamKeys(Collections.emptyList())
    //                                                 .team(Team.CD)
    //                                                 .templateId("msteams_test")
    //                                                 .templateData(Collections.emptyMap())
    //                                                 .userGroupIds(Collections.emptyList())
    //                                                 .build());

    //    notificationClient.testNotificationChannel(EmailSettingDTO.builder()
    //                                                   .accountId("kmpySmUISimoRrJL6NL73w")
    //                                                   .recipient("")
    //                                                   .subject("test-subject")
    //                                                   .body("test-body")
    //                                                   .build());
    //
    //    notificationClient.testNotificationChannel(
    //        SlackSettingDTO.builder()
    //            .accountId("kmpySmUISimoRrJL6NL73w")
    //            .recipient("")
    //            .build());
    //    notificationClient.testNotificationChannel(
    //        PagerDutySettingDTO.builder().accountId("kmpySmUISimoRrJL6NL73w").recipient("").build());
    //    notificationClient.testNotificationChannel(
    //        MSTeamSettingDTO.builder()
    //            .accountId("kmpySmUISimoRrJL6NL73w")
    //            .recipient(
    //                "")
    //            .build());
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(QueueListenerController.class));
  }

  private void registerCorsFilter(NotificationClientApplicationConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerJerseyProviders(Environment environment) {
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }
}
