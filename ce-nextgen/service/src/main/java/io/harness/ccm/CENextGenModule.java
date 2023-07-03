/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.audit.ResourceTypeConstants.BUDGET_GROUP;
import static io.harness.audit.ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE;
import static io.harness.audit.ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT;
import static io.harness.audit.ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE_SET;
import static io.harness.audit.ResourceTypeConstants.COST_CATEGORY;
import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE;
import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE_BUDGET;
import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE_FOLDER;
import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE_REPORT;
import static io.harness.authorization.AuthorizationServiceHeader.CE_NEXT_GEN;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.lock.DistributedLockImplementation.MONGO;

import io.harness.AccessControlClientModule;
import io.harness.accesscontrol.AccessControlAdminClientModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.retry.MethodExecutionHelper;
import io.harness.annotations.retry.RetryOnException;
import io.harness.annotations.retry.RetryOnExceptionInterceptor;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.ccm.audittrails.eventhandler.BudgetEventHandler;
import io.harness.ccm.audittrails.eventhandler.BudgetGroupEventHandler;
import io.harness.ccm.audittrails.eventhandler.CENextGenOutboxEventHandler;
import io.harness.ccm.audittrails.eventhandler.CostCategoryEventHandler;
import io.harness.ccm.audittrails.eventhandler.PerspectiveEventHandler;
import io.harness.ccm.audittrails.eventhandler.PerspectiveFolderEventHandler;
import io.harness.ccm.audittrails.eventhandler.ReportEventHandler;
import io.harness.ccm.audittrails.eventhandler.RuleEnforcementEventHandler;
import io.harness.ccm.audittrails.eventhandler.RuleEventHandler;
import io.harness.ccm.audittrails.eventhandler.RuleSetEventHandler;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.bigQuery.BigQueryServiceImpl;
import io.harness.ccm.budgetGroup.service.BudgetGroupService;
import io.harness.ccm.budgetGroup.service.BudgetGroupServiceImpl;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.clickHouse.ClickHouseServiceImpl;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.beans.config.GcpConfig;
import io.harness.ccm.commons.service.impl.ClusterRecordServiceImpl;
import io.harness.ccm.commons.service.impl.EntityMetadataServiceImpl;
import io.harness.ccm.commons.service.impl.InstanceDataServiceImpl;
import io.harness.ccm.commons.service.intf.ClusterRecordService;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.ccm.eventframework.ConnectorEntityCRUDStreamListener;
import io.harness.ccm.graphql.core.budget.BudgetCostService;
import io.harness.ccm.graphql.core.budget.BudgetCostServiceImpl;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.ccm.graphql.core.budget.BudgetServiceImpl;
import io.harness.ccm.graphql.core.currency.CurrencyPreferenceService;
import io.harness.ccm.graphql.core.currency.CurrencyPreferenceServiceImpl;
import io.harness.ccm.graphql.core.msp.impl.ManagedAccountDataServiceImpl;
import io.harness.ccm.graphql.core.msp.intf.ManagedAccountDataService;
import io.harness.ccm.jira.CCMJiraHelper;
import io.harness.ccm.jira.CCMJiraHelperImpl;
import io.harness.ccm.msp.service.impl.ManagedAccountServiceImpl;
import io.harness.ccm.msp.service.impl.MarginDetailsBqServiceImpl;
import io.harness.ccm.msp.service.impl.MarginDetailsServiceImpl;
import io.harness.ccm.msp.service.impl.MspValidationServiceImpl;
import io.harness.ccm.msp.service.intf.ManagedAccountService;
import io.harness.ccm.msp.service.intf.MarginDetailsBqService;
import io.harness.ccm.msp.service.intf.MarginDetailsService;
import io.harness.ccm.msp.service.intf.MspValidationService;
import io.harness.ccm.perpetualtask.K8sWatchTaskResourceClientModule;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.rbac.CCMRbacHelperImpl;
import io.harness.ccm.remote.mapper.anomaly.AnomalyFilterPropertiesMapper;
import io.harness.ccm.remote.mapper.governance.ExecutionFilterPropertyMapper;
import io.harness.ccm.remote.mapper.recommendation.CCMRecommendationFilterPropertiesMapper;
import io.harness.ccm.scheduler.SchedulerClientModule;
import io.harness.ccm.service.impl.AWSBucketPolicyHelperServiceImpl;
import io.harness.ccm.service.impl.AWSOrganizationHelperServiceImpl;
import io.harness.ccm.service.impl.AnomalyServiceImpl;
import io.harness.ccm.service.impl.AwsEntityChangeEventServiceImpl;
import io.harness.ccm.service.impl.AzureEntityChangeEventServiceImpl;
import io.harness.ccm.service.impl.CCMActiveSpendServiceImpl;
import io.harness.ccm.service.impl.CCMConnectorDetailsServiceImpl;
import io.harness.ccm.service.impl.CCMNotificationServiceImpl;
import io.harness.ccm.service.impl.CCMOverviewServiceImpl;
import io.harness.ccm.service.impl.CEYamlServiceImpl;
import io.harness.ccm.service.impl.GCPEntityChangeEventServiceImpl;
import io.harness.ccm.service.impl.LicenseUsageInterfaceImpl;
import io.harness.ccm.service.intf.AWSBucketPolicyHelperService;
import io.harness.ccm.service.intf.AWSOrganizationHelperService;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.ccm.service.intf.AwsEntityChangeEventService;
import io.harness.ccm.service.intf.AzureEntityChangeEventService;
import io.harness.ccm.service.intf.CCMActiveSpendService;
import io.harness.ccm.service.intf.CCMConnectorDetailsService;
import io.harness.ccm.service.intf.CCMNotificationService;
import io.harness.ccm.service.intf.CCMOverviewService;
import io.harness.ccm.service.intf.CEYamlService;
import io.harness.ccm.service.intf.GCPEntityChangeEventService;
import io.harness.ccm.serviceAccount.CEGcpServiceAccountService;
import io.harness.ccm.serviceAccount.CEGcpServiceAccountServiceImpl;
import io.harness.ccm.serviceAccount.GcpResourceManagerService;
import io.harness.ccm.serviceAccount.GcpResourceManagerServiceImpl;
import io.harness.ccm.serviceAccount.GcpServiceAccountService;
import io.harness.ccm.serviceAccount.GcpServiceAccountServiceImpl;
import io.harness.ccm.utils.AccountIdentifierLogInterceptor;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.businessmapping.service.impl.BusinessMappingHistoryServiceImpl;
import io.harness.ccm.views.businessmapping.service.impl.BusinessMappingServiceImpl;
import io.harness.ccm.views.businessmapping.service.impl.BusinessMappingValidationServiceImpl;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingHistoryService;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingValidationService;
import io.harness.ccm.views.service.CEReportScheduleService;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.DataResponseService;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.LabelFlattenedService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.service.impl.BigQueryDataResponseServiceImpl;
import io.harness.ccm.views.service.impl.CEReportScheduleServiceImpl;
import io.harness.ccm.views.service.impl.CEViewFolderServiceImpl;
import io.harness.ccm.views.service.impl.CEViewServiceImpl;
import io.harness.ccm.views.service.impl.ClickHouseDataResponseServiceImpl;
import io.harness.ccm.views.service.impl.ClickHouseViewsBillingServiceImpl;
import io.harness.ccm.views.service.impl.GovernanceRuleServiceImpl;
import io.harness.ccm.views.service.impl.LabelFlattenedServiceImpl;
import io.harness.ccm.views.service.impl.RuleEnforcementServiceImpl;
import io.harness.ccm.views.service.impl.RuleExecutionServiceImpl;
import io.harness.ccm.views.service.impl.RuleSetServiceImpl;
import io.harness.ccm.views.service.impl.ViewCustomFieldServiceImpl;
import io.harness.ccm.views.service.impl.ViewsBillingServiceImpl;
import io.harness.configuration.DeployMode;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.ff.FeatureFlagModule;
import io.harness.filter.FilterType;
import io.harness.filter.FiltersModule;
import io.harness.filter.impl.FilterServiceImpl;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.filter.service.FilterService;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.govern.ProviderModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.lock.DistributedLockImplementation;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.notification.module.NotificationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.queryconverter.SQLConverter;
import io.harness.queryconverter.SQLConverterImpl;
import io.harness.redis.RedisConfig;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.CENextGenModuleRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.timescaledb.JooqModule;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.timescaledb.metrics.HExecuteListener;
import io.harness.timescaledb.metrics.QueryStatsPrinter;
import io.harness.token.TokenClientModule;
import io.harness.version.VersionModule;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.jooq.ExecuteListener;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(CE)
public class CENextGenModule extends AbstractModule {
  private final CENextGenConfiguration configuration;

  public CENextGenModule(CENextGenConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CENextGenModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CENextGenModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CENextGenModuleRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(CENextGenModuleRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      MongoConfig eventsMongoConfig() {
        return configuration.getEventsMongoConfig();
      }

      @Provides
      @Singleton
      @Named("TimeScaleDBConfig")
      TimeScaleDBConfig timeScaleDBConfig() {
        return configuration.getTimeScaleDBConfig();
      }

      @Provides
      @Singleton
      @Named("PSQLExecuteListener")
      ExecuteListener executeListener() {
        return HExecuteListener.getInstance();
      }

      @Provides
      @Singleton
      @Named("gcpConfig")
      GcpConfig gcpConfig() {
        return configuration.getGcpConfig();
      }

      @Provides
      @Singleton
      @Named("clickHouseConfig")
      ClickHouseConfig clickHouseConfig() {
        return configuration.getClickHouseConfig();
      }

      @Provides
      @Singleton
      @Named("deployMode")
      DeployMode deployMode() {
        return configuration.getDeployMode();
      }

      @Provides
      @Singleton
      @Named("isClickHouseEnabled")
      boolean isClickHouseEnabled() {
        return configuration.isClickHouseEnabled();
      }

      @Provides
      @Singleton
      @Named("governanceConfig")
      io.harness.remote.GovernanceConfig governanceConfig() {
        return configuration.getGovernanceConfig();
      }
    });

    // Bind Services
    bind(CEYamlService.class).to(CEYamlServiceImpl.class);
    bind(AwsClient.class).to(AwsClientImpl.class);
    bind(GCPEntityChangeEventService.class).to(GCPEntityChangeEventServiceImpl.class);
    bind(AwsEntityChangeEventService.class).to(AwsEntityChangeEventServiceImpl.class);
    bind(AzureEntityChangeEventService.class).to(AzureEntityChangeEventServiceImpl.class);
    bind(BusinessMappingService.class).to(BusinessMappingServiceImpl.class);
    bind(BusinessMappingHistoryService.class).to(BusinessMappingHistoryServiceImpl.class);
    bind(BusinessMappingValidationService.class).to(BusinessMappingValidationServiceImpl.class);
    bind(LicenseUsageInterface.class).to(LicenseUsageInterfaceImpl.class).in(Singleton.class);

    install(new CENextGenPersistenceModule());
    install(ExecutorModule.getInstance());
    install(FiltersModule.getInstance());
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    install(new ConnectorResourceClientModule(configuration.getNgManagerClientConfig(),
        configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId(), ClientMode.PRIVILEGED));
    install(new LightwingClientModule(configuration.getLightwingAutoCUDClientConfig(),
        configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId(), ClientMode.PRIVILEGED));
    install(new SchedulerClientModule(configuration.getDkronClientConfig(), configuration.getNgManagerServiceSecret(),
        CE_NEXT_GEN.getServiceId(), ClientMode.PRIVILEGED));
    install(new K8sWatchTaskResourceClientModule(
        configuration.getManagerClientConfig(), configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId()));
    install(new TokenClientModule(configuration.getNgManagerClientConfig(), configuration.getNgManagerServiceSecret(),
        CE_NEXT_GEN.getServiceId()));
    install(EnforcementClientModule.getInstance(configuration.getNgManagerClientConfig(),
        configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId(),
        configuration.getEnforcementClientConfiguration()));
    install(new MetricsModule());
    install(AccessControlClientModule.getInstance(
        configuration.getAccessControlClientConfiguration(), CE_NEXT_GEN.getServiceId()));
    install(new AccessControlAdminClientModule(
        configuration.getAccessControlAdminClientConfiguration(), CE_NEXT_GEN.getServiceId()));

    install(new SecretNGManagerClientModule(configuration.getNgManagerClientConfig(),
        configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId()));
    install(VersionModule.getInstance());
    install(PrimaryVersionManagerModule.getInstance());
    install(new ValidationModule(getValidatorFactory()));
    install(TimeModule.getInstance());
    install(FeatureFlagModule.getInstance());
    install(new EventsFrameworkModule(configuration.getEventsFrameworkConfiguration()));
    install(JooqModule.getInstance());
    install(new NotificationClientModule(configuration.getNotificationClientConfiguration()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return configuration.getSegmentConfiguration();
      }
    });
    install(new AuditClientModule(configuration.getAuditClientConfig(), configuration.getNgManagerServiceSecret(),
        NG_MANAGER.getServiceId(), configuration.isEnableAudit()));
    install(new TransactionOutboxModule(
        configuration.getOutboxPollConfig(), NG_MANAGER.getServiceId(), configuration.isExportMetricsToStackDriver()));
    install(NgLicenseHttpClientModule.getInstance(configuration.getNgManagerClientConfig(),
        configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId()));
    install(new CENGGraphQLModule(configuration.getCurrencyPreferencesConfig()));
    install(new AccountClientModule(
        configuration.getManagerClientConfig(), configuration.getNgManagerServiceSecret(), CE_NEXT_GEN.getServiceId()));
    install(YamlSdkModule.getInstance());
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(CENextGenConfiguration.class).toInstance(configuration);
    bind(SQLConverter.class).to(SQLConverterImpl.class);
    bind(BigQueryService.class).to(BigQueryServiceImpl.class);
    bind(CEGcpServiceAccountService.class).to(CEGcpServiceAccountServiceImpl.class);
    bind(GcpServiceAccountService.class).to(GcpServiceAccountServiceImpl.class);
    bind(GcpResourceManagerService.class).to(GcpResourceManagerServiceImpl.class);
    bind(CEViewService.class).to(CEViewServiceImpl.class);
    bind(CEViewFolderService.class).to(CEViewFolderServiceImpl.class);
    bind(ClusterRecordService.class).to(ClusterRecordServiceImpl.class);
    bind(ViewCustomFieldService.class).to(ViewCustomFieldServiceImpl.class);
    bind(CEReportScheduleService.class).to(CEReportScheduleServiceImpl.class);
    bind(QueryStatsPrinter.class).toInstance(HExecuteListener.getInstance());
    bind(AWSOrganizationHelperService.class).to(AWSOrganizationHelperServiceImpl.class);
    bind(AWSBucketPolicyHelperService.class).to(AWSBucketPolicyHelperServiceImpl.class);
    bind(BudgetService.class).to(BudgetServiceImpl.class);
    bind(InstanceDataService.class).to(InstanceDataServiceImpl.class);
    bind(BudgetCostService.class).to(BudgetCostServiceImpl.class);
    bind(EntityMetadataService.class).to(EntityMetadataServiceImpl.class);
    bind(CCMConnectorDetailsService.class).to(CCMConnectorDetailsServiceImpl.class);
    bind(AnomalyService.class).to(AnomalyServiceImpl.class);
    bind(CCMNotificationService.class).to(CCMNotificationServiceImpl.class);
    bind(FilterService.class).to(FilterServiceImpl.class);
    registerOutboxEventHandlers();
    bind(OutboxEventHandler.class).to(CENextGenOutboxEventHandler.class);
    bind(CCMRbacHelper.class).to(CCMRbacHelperImpl.class);
    bind(GovernanceRuleService.class).to(GovernanceRuleServiceImpl.class);
    bind(RuleSetService.class).to(RuleSetServiceImpl.class);
    bind(RuleEnforcementService.class).to(RuleEnforcementServiceImpl.class);
    bind(RuleExecutionService.class).to(RuleExecutionServiceImpl.class);
    bind(CCMActiveSpendService.class).to(CCMActiveSpendServiceImpl.class);
    bind(CCMJiraHelper.class).to(CCMJiraHelperImpl.class);
    bind(CurrencyPreferenceService.class).to(CurrencyPreferenceServiceImpl.class);
    bind(BudgetGroupService.class).to(BudgetGroupServiceImpl.class);
    bind(ClickHouseService.class).to(ClickHouseServiceImpl.class);
    bind(CCMOverviewService.class).to(CCMOverviewServiceImpl.class);
    bind(ManagedAccountService.class).to(ManagedAccountServiceImpl.class);
    bind(MarginDetailsService.class).to(MarginDetailsServiceImpl.class);
    bind(ManagedAccountDataService.class).to(ManagedAccountDataServiceImpl.class);
    bind(MarginDetailsBqService.class).to(MarginDetailsBqServiceImpl.class);
    bind(MspValidationService.class).to(MspValidationServiceImpl.class);
    bind(LabelFlattenedService.class).to(LabelFlattenedServiceImpl.class);

    if (configuration.isClickHouseEnabled()) {
      bind(ViewsBillingService.class).to(ClickHouseViewsBillingServiceImpl.class);
      bind(DataResponseService.class).to(ClickHouseDataResponseServiceImpl.class);
    } else {
      bind(ViewsBillingService.class).to(ViewsBillingServiceImpl.class);
      bind(DataResponseService.class).to(BigQueryDataResponseServiceImpl.class);
    }

    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }

    registerEventsFrameworkMessageListeners();

    bindRetryOnExceptionInterceptor();

    bindAccountLogContextInterceptor();

    registerDelegateTaskService();

    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.CCMRECOMMENDATION.toString())
        .to(CCMRecommendationFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.ANOMALY.toString()).to(AnomalyFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.RULEEXECUTION.toString()).to(ExecutionFilterPropertyMapper.class);
  }

  private void bindAccountLogContextInterceptor() {
    AccountIdentifierLogInterceptor accountIdentifierLogInterceptor = new AccountIdentifierLogInterceptor();
    requestInjection(accountIdentifierLogInterceptor);
    bindInterceptor(
        Matchers.any(), Matchers.annotatedWith(LogAccountIdentifier.class), accountIdentifierLogInterceptor);
  }

  private void registerOutboxEventHandlers() {
    MapBinder<String, OutboxEventHandler> outboxEventHandlerMapBinder =
        MapBinder.newMapBinder(binder(), String.class, OutboxEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PERSPECTIVE).to(PerspectiveEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PERSPECTIVE_BUDGET).to(BudgetEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PERSPECTIVE_FOLDER).to(PerspectiveFolderEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(PERSPECTIVE_REPORT).to(ReportEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(COST_CATEGORY).to(CostCategoryEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(CLOUD_ASSET_GOVERNANCE_RULE).to(RuleEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(CLOUD_ASSET_GOVERNANCE_RULE_SET).to(RuleSetEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT)
        .to(RuleEnforcementEventHandler.class);
    outboxEventHandlerMapBinder.addBinding(BUDGET_GROUP).to(BudgetGroupEventHandler.class);
  }

  private void registerDelegateTaskService() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
          DelegateServiceGrpcClient delegateServiceGrpcClient) {
        return (Supplier<DelegateCallbackToken>) Suppliers.memoize(
            () -> getDelegateCallbackToken(delegateServiceGrpcClient, configuration));
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "CENextGen_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "CENextGen_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "CENextGen_delegateTaskProgressResponses")
            .build();
      }
    });

    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.MORPHIA).build();
      }
    });

    install(DelegateServiceDriverModule.getInstance(false, false));
    install(new DelegateServiceDriverGrpcClientModule(configuration.getNgManagerServiceSecret(),
        configuration.getGrpcClientConfig().getTarget(), configuration.getGrpcClientConfig().getAuthority(), true));
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, CENextGenConfiguration configuration) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix(CE_NEXT_GEN.getServiceId())
                                  .setConnection(configuration.getEventsMongoConfig().getUri())
                                  .build())
            .build());
    log.info("Delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  private void bindRetryOnExceptionInterceptor() {
    bind(MethodExecutionHelper.class).asEagerSingleton();
    ProviderMethodInterceptor retryOnExceptionInterceptor =
        new ProviderMethodInterceptor(getProvider(RetryOnExceptionInterceptor.class));
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(RetryOnException.class), retryOnExceptionInterceptor);
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return configuration.getDistributedLockImplementation() == null ? MONGO
                                                                    : configuration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return configuration.getRedisLockConfig();
  }

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }

  private void registerEventsFrameworkMessageListeners() {
    bind(MessageListener.class)
        .annotatedWith(Names.named(CONNECTOR_ENTITY + ENTITY_CRUD))
        .to(ConnectorEntityCRUDStreamListener.class);
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder().addAll(CENextGenModuleRegistrars.yamlSchemaRegistrars).build();
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    CENextGenApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapperWithoutNamed() {
    return Jackson.newObjectMapper();
  }
}
