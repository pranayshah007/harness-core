/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;

import io.harness.config.PublisherConfiguration;
import io.harness.event.model.GenericEvent;
import io.harness.mongo.queue.QueueFactory;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;
import io.harness.queue.QueuePublisher;
import io.harness.version.VersionInfoManager;

import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentStepTimeSeriesEvent;
import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.api.ExecutionInterruptTimeSeriesEvent;
import software.wings.api.InstanceEvent;
import software.wings.collect.ArtifactCollectEventListener;
import software.wings.collect.CollectEvent;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.notification.EmailNotificationListener;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.impl.ExecutionEvent;
import software.wings.service.impl.ExecutionEventListener;
import software.wings.service.impl.event.DeploymentStepTimeSeriesEventListener;
import software.wings.service.impl.event.DeploymentTimeSeriesEventListener;
import software.wings.service.impl.event.ExecutionInterruptTimeSeriesEventListener;
import software.wings.service.impl.event.GenericEventListener;
import software.wings.service.impl.instance.DeploymentEventListener;
import software.wings.service.impl.instance.InstanceEventListener;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

public class ManagerQueueModule extends AbstractModule {
  @Provides
  @Singleton
  QueuePublisher<PruneEvent> pruneQueuePublisher(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(injector, PruneEvent.class, null, config);
  }

  @Provides
  @Singleton
  QueueConsumer<PruneEvent> pruneQueueConsumer(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, PruneEvent.class, ofSeconds(5), null, config);
  }

  @Provides
  @Singleton
  QueuePublisher<EmailData> emailQueuePublisher(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(injector, EmailData.class, null, config);
  }

  @Provides
  @Singleton
  QueueConsumer<EmailData> emailQueueConsumer(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, EmailData.class, ofSeconds(5), null, config);
  }

  @Provides
  @Singleton
  QueuePublisher<CollectEvent> collectQueuePublisher(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(injector, CollectEvent.class, null, config);
  }

  @Provides
  @Singleton
  QueueConsumer<CollectEvent> collectQueueConsumer(Injector injector, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, CollectEvent.class, ofSeconds(5), null, config);
  }

  @Provides
  @Singleton
  QueuePublisher<DeploymentEvent> deploymentQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(
        injector, DeploymentEvent.class, asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<DeploymentEvent> deploymentQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, DeploymentEvent.class, ofMinutes(1),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Provides
  @Singleton
  QueuePublisher<ExecutionEvent> executionQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(
        injector, ExecutionEvent.class, asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<ExecutionEvent> executionQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, ExecutionEvent.class, ofSeconds(30),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Provides
  @Singleton
  QueuePublisher<GenericEvent> genericQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(
        injector, GenericEvent.class, asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<GenericEvent> genericQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, GenericEvent.class, ofMinutes(1),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Provides
  @Singleton
  QueuePublisher<InstanceEvent> instanceQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(
        injector, InstanceEvent.class, asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<InstanceEvent> instanceQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, InstanceEvent.class, ofMinutes(1),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Provides
  @Singleton
  QueuePublisher<DeploymentTimeSeriesEvent> deploymentTimeSeriesQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(
        injector, DeploymentTimeSeriesEvent.class, asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<DeploymentTimeSeriesEvent> deploymentTimeSeriesQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, DeploymentTimeSeriesEvent.class, ofMinutes(1),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Provides
  @Singleton
  QueuePublisher<DeploymentStepTimeSeriesEvent> deploymentStepTimeSeriesQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(injector, DeploymentStepTimeSeriesEvent.class,
        asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<DeploymentStepTimeSeriesEvent> deploymentStepTimeSeriesQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, DeploymentStepTimeSeriesEvent.class, ofMinutes(1),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Provides
  @Singleton
  QueuePublisher<ExecutionInterruptTimeSeriesEvent> executionInterruptTimeSeriesQueuePublisher(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueuePublisher(injector, ExecutionInterruptTimeSeriesEvent.class,
        asList(versionInfoManager.getVersionInfo().getVersion()), config);
  }

  @Provides
  @Singleton
  QueueConsumer<ExecutionInterruptTimeSeriesEvent> executionInterruptTimeSeriesQueueConsumer(
      Injector injector, VersionInfoManager versionInfoManager, PublisherConfiguration config) {
    return QueueFactory.createQueueConsumer(injector, ExecutionInterruptTimeSeriesEvent.class, ofMinutes(1),
        asList(asList(versionInfoManager.getVersionInfo().getVersion())), config);
  }

  @Override
  protected void configure() {
    bind(new TypeLiteral<QueueListener<PruneEvent>>() {}).to(PruneEntityListener.class);
    bind(new TypeLiteral<QueueListener<EmailData>>() {}).to(EmailNotificationListener.class);
    bind(new TypeLiteral<QueueListener<CollectEvent>>() {}).to(ArtifactCollectEventListener.class);
    bind(new TypeLiteral<QueueListener<DeploymentEvent>>() {}).to(DeploymentEventListener.class);
    bind(new TypeLiteral<QueueListener<ExecutionEvent>>() {}).to(ExecutionEventListener.class);
    bind(new TypeLiteral<QueueListener<GenericEvent>>() {}).to(GenericEventListener.class);
    bind(new TypeLiteral<QueueListener<InstanceEvent>>() {}).to(InstanceEventListener.class);
    bind(new TypeLiteral<QueueListener<DeploymentTimeSeriesEvent>>() {}).to(DeploymentTimeSeriesEventListener.class);
    bind(new TypeLiteral<QueueListener<DeploymentStepTimeSeriesEvent>>() {
    }).to(DeploymentStepTimeSeriesEventListener.class);
    bind(new TypeLiteral<QueueListener<ExecutionInterruptTimeSeriesEvent>>() {
    }).to(ExecutionInterruptTimeSeriesEventListener.class);
  }
}
