/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.accesscontrol.AccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.VIEW_ORGANIZATION_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeLevel.ACCOUNT;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeLevel.ORGANIZATION;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeLevel.PROJECT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_GROUP_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_TOPIC_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.lock.DistributedLockImplementation.MONGO;

import io.harness.AccessControlClientModule;
import io.harness.accesscontrol.acl.ResourceAttributeProvider;
import io.harness.accesscontrol.acl.api.ACLResource;
import io.harness.accesscontrol.acl.api.ACLResourceImpl;
import io.harness.accesscontrol.acl.api.ResourceAttributeProviderImpl;
import io.harness.accesscontrol.admin.api.AccessControlAdminResource;
import io.harness.accesscontrol.admin.api.AccessControlAdminResourceImpl;
import io.harness.accesscontrol.aggregator.api.AggregatorResource;
import io.harness.accesscontrol.aggregator.api.AggregatorResourceImpl;
import io.harness.accesscontrol.aggregator.consumers.AccessControlChangeEventFailureHandler;
import io.harness.accesscontrol.commons.events.EventConsumer;
import io.harness.accesscontrol.commons.events.EventsConfig;
import io.harness.accesscontrol.commons.iterators.AccessControlIteratorsConfig;
import io.harness.accesscontrol.commons.notifications.NotificationConfig;
import io.harness.accesscontrol.commons.outbox.AccessControlOutboxEventHandler;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.commons.version.MockQueueController;
import io.harness.accesscontrol.health.HealthResource;
import io.harness.accesscontrol.health.HealthResourceImpl;
import io.harness.accesscontrol.permissions.api.PermissionResource;
import io.harness.accesscontrol.permissions.api.PermissionResourceImpl;
import io.harness.accesscontrol.preference.AccessControlPreferenceModule;
import io.harness.accesscontrol.preference.api.AccessControlPreferenceResource;
import io.harness.accesscontrol.preference.api.AccessControlPreferenceResourceImpl;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.PrincipalValidator;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountServiceImpl;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountValidator;
import io.harness.accesscontrol.principals.serviceaccounts.events.ServiceAccountEventConsumer;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupService;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupServiceImpl;
import io.harness.accesscontrol.principals.usergroups.UserGroupValidator;
import io.harness.accesscontrol.principals.usergroups.events.UserGroupEventConsumer;
import io.harness.accesscontrol.principals.users.HarnessUserService;
import io.harness.accesscontrol.principals.users.HarnessUserServiceImpl;
import io.harness.accesscontrol.principals.users.UserValidator;
import io.harness.accesscontrol.principals.users.events.UserMembershipEventConsumer;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupServiceImpl;
import io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupEventConsumer;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.api.AccountRoleAssignmentsApiImpl;
import io.harness.accesscontrol.roleassignments.api.OrgRoleAssignmentsApiImpl;
import io.harness.accesscontrol.roleassignments.api.ProjectRoleAssignmentsApiImpl;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResource;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResourceImpl;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentHandler;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentService;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentServiceImpl;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDao;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDaoImpl;
import io.harness.accesscontrol.roleassignments.validation.RoleAssignmentActionValidator;
import io.harness.accesscontrol.roles.api.AccountRolesApiImpl;
import io.harness.accesscontrol.roles.api.OrgRolesApiImpl;
import io.harness.accesscontrol.roles.api.ProjectRolesApiImpl;
import io.harness.accesscontrol.roles.api.RoleResource;
import io.harness.accesscontrol.roles.api.RoleResourceImpl;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeServiceImpl;
import io.harness.accesscontrol.scopes.harness.events.ScopeEventConsumer;
import io.harness.accesscontrol.support.SupportService;
import io.harness.accesscontrol.support.SupportServiceImpl;
import io.harness.accesscontrol.support.persistence.SupportPreferenceDao;
import io.harness.accesscontrol.support.persistence.SupportPreferenceDaoImpl;
import io.harness.account.AccountClientModule;
import io.harness.aggregator.AggregatorModule;
import io.harness.aggregator.consumers.ChangeEventFailureHandler;
import io.harness.aggregator.consumers.RoleAssignmentCRUDEventHandler;
import io.harness.aggregator.consumers.UserGroupCRUDEventHandler;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.environment.EnvironmentResourceClientModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.monitoring.publisher.RedisEventMetricPublisher;
import io.harness.ff.FeatureFlagClientModule;
import io.harness.ff.FeatureFlagModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.project.ProjectClientModule;
import io.harness.queue.QueueController;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.serviceaccount.ServiceAccountClientModule;
import io.harness.spec.server.accesscontrol.v1.AccountRoleAssignmentsApi;
import io.harness.spec.server.accesscontrol.v1.AccountRolesApi;
import io.harness.spec.server.accesscontrol.v1.OrgRoleAssignmentsApi;
import io.harness.spec.server.accesscontrol.v1.OrganizationRolesApi;
import io.harness.spec.server.accesscontrol.v1.ProjectRoleAssignmentsApi;
import io.harness.spec.server.accesscontrol.v1.ProjectRolesApi;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;
import io.harness.usergroups.UserGroupClientModule;
import io.harness.usermembership.UserMembershipClientModule;
import io.harness.version.VersionModule;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.redisson.api.RedissonClient;
import ru.vyarus.guice.validator.ValidationModule;

@OwnedBy(PL)
@Slf4j
public class AccessControlModule extends AbstractModule {
  private static AccessControlModule instance;
  private final AccessControlConfiguration config;

  private AccessControlModule(AccessControlConfiguration config) {
    this.config = config;
  }

  public static synchronized AccessControlModule getInstance(AccessControlConfiguration config) {
    if (instance == null) {
      instance = new AccessControlModule(config);
    }
    return instance;
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return config.getDistributedLockImplementation() == null ? MONGO : config.getDistributedLockImplementation();
  }

  @Provides
  @Singleton
  NotificationConfig notificationConfig() {
    return config.getNotificationConfig();
  }

  @Provides
  @Named("lock")
  @Singleton
  public RedisConfig redisLockConfig() {
    return config.getRedisLockConfig();
  }

  @Provides
  @Named("eventsConfig")
  @Singleton
  public EventsConfig eventsConfig() {
    return config.getEventsConfig();
  }

  @Provides
  @Named("eventsFrameworkRedissonClient")
  @Singleton
  public RedissonClient getRedissonClient() {
    RedisConfig redisConfig = config.getEventsConfig().getRedisConfig();
    if (config.getEventsConfig().isEnabled()) {
      return RedissonClientFactory.getClient(redisConfig);
    }
    return null;
  }

  @Provides
  @Named(ENTITY_CRUD)
  @Singleton
  public Consumer getEntityCrudConsumer(@Nullable @Named("eventsFrameworkRedissonClient") RedissonClient redissonClient,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    RedisConfig redisConfig = config.getEventsConfig().getRedisConfig();
    if (!config.getEventsConfig().isEnabled()) {
      return NoOpConsumer.of(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME);
    }
    return RedisConsumer.of(ENTITY_CRUD, ACCESS_CONTROL_SERVICE.getServiceId(), redissonClient,
        ENTITY_CRUD_MAX_PROCESSING_TIME, ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace(),
        redisEventMetricPublisher);
  }

  @Provides
  @Named(USERMEMBERSHIP)
  @Singleton
  public Consumer getUserMembershipConsumer(
      @Nullable @Named("eventsFrameworkRedissonClient") RedissonClient redissonClient,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    RedisConfig redisConfig = config.getEventsConfig().getRedisConfig();
    if (!config.getEventsConfig().isEnabled()) {
      return NoOpConsumer.of(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME);
    }
    return RedisConsumer.of(USERMEMBERSHIP, ACCESS_CONTROL_SERVICE.getServiceId(), redissonClient,
        Duration.ofMinutes(10), 3, redisConfig.getEnvNamespace(), redisEventMetricPublisher);
  }

  @Provides
  public AccessControlIteratorsConfig getIteratorsConfig() {
    return config.getIteratorsConfig();
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance());
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        5, 100, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));
    install(ExecutorModule.getInstance());
    install(PersistentLockModule.getInstance());
    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();
    install(new ValidationModule(validatorFactory));
    install(new MetricsModule());
    install(
        new ServiceAccountClientModule(config.getServiceAccountClientConfiguration().getServiceAccountServiceConfig(),
            config.getServiceAccountClientConfiguration().getServiceAccountServiceSecret(),
            ACCESS_CONTROL_SERVICE.getServiceId()));

    install(AccessControlClientModule.getInstance(
        config.getAccessControlClientConfiguration(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new ResourceGroupClientModule(config.getResourceGroupClientConfiguration().getResourceGroupServiceConfig(),
        config.getResourceGroupClientConfiguration().getResourceGroupServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new UserGroupClientModule(config.getUserGroupClientConfiguration().getUserGroupServiceConfig(),
        config.getUserGroupClientConfiguration().getUserGroupServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new UserMembershipClientModule(config.getUserClientConfiguration().getUserServiceConfig(),
        config.getUserClientConfiguration().getUserServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new AuditClientModule(config.getAuditClientConfig(), config.getDefaultServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId(), config.isEnableAudit()));

    install(new AccountClientModule(config.getAccountClientConfiguration().getAccountServiceConfig(),
        config.getAccountClientConfiguration().getAccountServiceSecret(), ACCESS_CONTROL_SERVICE.toString()));

    install(new ProjectClientModule(config.getProjectClientConfiguration().getProjectServiceConfig(),
        config.getProjectClientConfiguration().getProjectServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new OrganizationClientModule(config.getOrganizationClientConfiguration().getOrganizationServiceConfig(),
        config.getOrganizationClientConfiguration().getOrganizationServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new TokenClientModule(config.getServiceAccountClientConfiguration().getServiceAccountServiceConfig(),
        config.getServiceAccountClientConfiguration().getServiceAccountServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId()));

    install(
        FeatureFlagClientModule.getInstance(config.getFeatureFlagClientConfiguration().getFeatureFlagServiceConfig(),
            config.getFeatureFlagClientConfiguration().getFeatureFlagServiceSecret(),
            ACCESS_CONTROL_SERVICE.getServiceId()));

    install(
        EnforcementClientModule.getInstance(config.getOrganizationClientConfiguration().getOrganizationServiceConfig(),
            config.getOrganizationClientConfiguration().getOrganizationServiceSecret(),
            ACCESS_CONTROL_SERVICE.getServiceId(), config.getEnforcementClientConfiguration()));

    install(new EnvironmentResourceClientModule(config.getNgManagerServiceConfiguration().getNgManagerServiceConfig(),
        config.getNgManagerServiceConfiguration().getNgManagerServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId(),
        ClientMode.PRIVILEGED));

    install(new ConnectorResourceClientModule(config.getNgManagerServiceConfiguration().getNgManagerServiceConfig(),
        config.getNgManagerServiceConfiguration().getNgManagerServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId(),
        ClientMode.PRIVILEGED));

    install(new TransactionOutboxModule(config.getOutboxPollConfig(), ACCESS_CONTROL_SERVICE.getServiceId(),
        config.getAggregatorConfiguration().isExportMetricsToStackDriver()));
    install(NGMigrationSdkModule.getInstance());

    install(AccessControlPersistenceModule.getInstance(config.getMongoConfig()));
    install(AccessControlCoreModule.getInstance());
    install(AccessControlPreferenceModule.getInstance());
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return config.getSegmentConfiguration();
      }
    });
    install(FeatureFlagModule.getInstance());
    if (config.getAggregatorConfiguration().isEnabled()) {
      install(AggregatorModule.getInstance(config.getAggregatorConfiguration()));
      bind(ChangeEventFailureHandler.class).to(AccessControlChangeEventFailureHandler.class);
    }
    install(UserClientModule.getInstance(config.getAccountClientConfiguration().getAccountServiceConfig(),
        config.getAccountClientConfiguration().getAccountServiceSecret(), ACCESS_CONTROL_SERVICE.toString()));

    bind(TimeLimiter.class).toInstance(HTimeLimiter.create());

    bind(OutboxEventHandler.class).to(AccessControlOutboxEventHandler.class);

    MapBinder<String, ScopeLevel> scopesByKey = MapBinder.newMapBinder(binder(), String.class, ScopeLevel.class);
    scopesByKey.addBinding(ACCOUNT.toString()).toInstance(ACCOUNT);
    scopesByKey.addBinding(ORGANIZATION.toString()).toInstance(ORGANIZATION);
    scopesByKey.addBinding(PROJECT.toString()).toInstance(PROJECT);

    bind(HarnessScopeService.class).to(HarnessScopeServiceImpl.class);

    bind(HarnessResourceGroupService.class).to(HarnessResourceGroupServiceImpl.class);
    bind(HarnessUserGroupService.class).to(HarnessUserGroupServiceImpl.class);
    bind(HarnessUserService.class).to(HarnessUserServiceImpl.class);
    bind(HarnessServiceAccountService.class).to(HarnessServiceAccountServiceImpl.class);

    bind(UserGroupCRUDEventHandler.class).to(PrivilegedRoleAssignmentHandler.class);
    bind(RoleAssignmentCRUDEventHandler.class).to(PrivilegedRoleAssignmentHandler.class);

    MapBinder<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Pair<ScopeLevel, Boolean>>() {}, new TypeLiteral<Set<String>>() {});
    implicitPermissionsByScope.addBinding(Pair.of(ACCOUNT, true))
        .toInstance(Sets.newHashSet(VIEW_ACCOUNT_PERMISSION, VIEW_ORGANIZATION_PERMISSION, VIEW_PROJECT_PERMISSION));
    implicitPermissionsByScope.addBinding(Pair.of(ACCOUNT, false))
        .toInstance(Collections.singleton(VIEW_ACCOUNT_PERMISSION));
    implicitPermissionsByScope.addBinding(Pair.of(ORGANIZATION, true))
        .toInstance(Sets.newHashSet(VIEW_ORGANIZATION_PERMISSION, VIEW_PROJECT_PERMISSION));
    implicitPermissionsByScope.addBinding(Pair.of(ORGANIZATION, false))
        .toInstance(Collections.singleton(VIEW_ORGANIZATION_PERMISSION));
    implicitPermissionsByScope.addBinding(Pair.of(PROJECT, true))
        .toInstance(Collections.singleton(VIEW_PROJECT_PERMISSION));
    implicitPermissionsByScope.addBinding(Pair.of(PROJECT, false))
        .toInstance(Collections.singleton(VIEW_PROJECT_PERMISSION));

    MapBinder<PrincipalType, PrincipalValidator> validatorByPrincipalType =
        MapBinder.newMapBinder(binder(), PrincipalType.class, PrincipalValidator.class);
    validatorByPrincipalType.addBinding(USER).to(UserValidator.class);
    validatorByPrincipalType.addBinding(USER_GROUP).to(UserGroupValidator.class);
    validatorByPrincipalType.addBinding(SERVICE_ACCOUNT).to(ServiceAccountValidator.class);

    Multibinder<EventConsumer> entityCrudEventConsumers =
        Multibinder.newSetBinder(binder(), EventConsumer.class, Names.named(ENTITY_CRUD));
    entityCrudEventConsumers.addBinding().to(ResourceGroupEventConsumer.class);
    entityCrudEventConsumers.addBinding().to(UserGroupEventConsumer.class);
    entityCrudEventConsumers.addBinding().to(ServiceAccountEventConsumer.class);
    entityCrudEventConsumers.addBinding().to(ScopeEventConsumer.class);

    Multibinder<EventConsumer> userMembershipEventConsumers =
        Multibinder.newSetBinder(binder(), EventConsumer.class, Names.named(USERMEMBERSHIP));
    userMembershipEventConsumers.addBinding().to(UserMembershipEventConsumer.class);

    binder()
        .bind(new TypeLiteral<HarnessActionValidator<RoleAssignment>>() {})
        .annotatedWith(Names.named(RoleAssignmentDTO.MODEL_NAME))
        .to(RoleAssignmentActionValidator.class);

    bind(SupportPreferenceDao.class).to(SupportPreferenceDaoImpl.class);
    bind(SupportService.class).to(SupportServiceImpl.class);

    bind(PrivilegedRoleAssignmentDao.class).to(PrivilegedRoleAssignmentDaoImpl.class);
    bind(PrivilegedRoleAssignmentService.class).to(PrivilegedRoleAssignmentServiceImpl.class);
    bind(ResourceAttributeProvider.class).to(ResourceAttributeProviderImpl.class);

    bind(QueueController.class).to(MockQueueController.class);

    bind(ACLResource.class).to(ACLResourceImpl.class);
    bind(AggregatorResource.class).to(AggregatorResourceImpl.class);
    bind(AccessControlAdminResource.class).to(AccessControlAdminResourceImpl.class);
    bind(HealthResource.class).to(HealthResourceImpl.class);
    bind(PermissionResource.class).to(PermissionResourceImpl.class);
    bind(AccessControlPreferenceResource.class).to(AccessControlPreferenceResourceImpl.class);
    bind(RoleAssignmentResource.class).to(RoleAssignmentResourceImpl.class);
    bind(RoleResource.class).to(RoleResourceImpl.class);
    bind(AccountRolesApi.class).to(AccountRolesApiImpl.class);
    bind(OrganizationRolesApi.class).to(OrgRolesApiImpl.class);
    bind(ProjectRolesApi.class).to(ProjectRolesApiImpl.class);
    bind(AccountRoleAssignmentsApi.class).to(AccountRoleAssignmentsApiImpl.class);
    bind(OrgRoleAssignmentsApi.class).to(OrgRoleAssignmentsApiImpl.class);
    bind(ProjectRoleAssignmentsApi.class).to(ProjectRoleAssignmentsApiImpl.class);

    bind(boolean.class).annotatedWith(Names.named("disableRedundantACLs")).toInstance(config.isDisableRedundantACLs());
    bind(boolean.class)
        .annotatedWith(Names.named("enableParallelProcessingOfUserGroupUpdates"))
        .toInstance(config.isEnableParallelProcessingOfUserGroupUpdates());
  }
}
