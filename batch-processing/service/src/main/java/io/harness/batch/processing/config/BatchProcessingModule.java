/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import static io.harness.authorization.AuthorizationServiceHeader.BATCH_PROCESSING;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;

import io.harness.account.AccountClient;
import io.harness.account.AccountClientModule;
import io.harness.annotations.retry.MethodExecutionHelper;
import io.harness.annotations.retry.RetryOnException;
import io.harness.annotations.retry.RetryOnExceptionInterceptor;
import io.harness.batch.processing.metrics.CENGTelemetryService;
import io.harness.batch.processing.metrics.CENGTelemetryServiceImpl;
import io.harness.batch.processing.metrics.CeCloudMetricsService;
import io.harness.batch.processing.metrics.CeCloudMetricsServiceImpl;
import io.harness.batch.processing.metrics.ProductMetricsService;
import io.harness.batch.processing.metrics.ProductMetricsServiceImpl;
import io.harness.batch.processing.svcmetrics.BatchProcessingMetricsPublisher;
import io.harness.batch.processing.tasklet.util.ClusterHelper;
import io.harness.batch.processing.tasklet.util.ClusterHelperImpl;
import io.harness.batch.processing.tasklet.util.CurrencyPreferenceHelper;
import io.harness.batch.processing.tasklet.util.CurrencyPreferenceHelperImpl;
import io.harness.ccm.CENGGraphQLModule;
import io.harness.ccm.anomaly.service.impl.AnomalyServiceImpl;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.ccm.azurevmpricing.AzureVmPricingClientModule;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.bigQuery.BigQueryServiceImpl;
import io.harness.ccm.budgetGroup.service.BudgetGroupService;
import io.harness.ccm.budgetGroup.service.BudgetGroupServiceImpl;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.clickHouse.ClickHouseServiceImpl;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.dao.recommendation.RecommendationCrudService;
import io.harness.ccm.commons.dao.recommendation.RecommendationCrudServiceImpl;
import io.harness.ccm.commons.service.impl.ClusterRecordServiceImpl;
import io.harness.ccm.commons.service.impl.EntityMetadataServiceImpl;
import io.harness.ccm.commons.service.impl.InstanceDataServiceImpl;
import io.harness.ccm.commons.service.intf.ClusterRecordService;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.CESlackWebhookServiceImpl;
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
import io.harness.ccm.service.impl.AWSOrganizationHelperServiceImpl;
import io.harness.ccm.service.intf.AWSOrganizationHelperService;
import io.harness.ccm.serviceNow.CCMServiceNowHelper;
import io.harness.ccm.serviceNow.CCMServiceNowHelperImpl;
import io.harness.ccm.views.businessmapping.service.impl.BusinessMappingHistoryServiceImpl;
import io.harness.ccm.views.businessmapping.service.impl.BusinessMappingServiceImpl;
import io.harness.ccm.views.businessmapping.service.impl.BusinessMappingValidationServiceImpl;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingHistoryService;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingValidationService;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.DataResponseService;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.LabelFlattenedService;
import io.harness.ccm.views.service.PerspectiveAnomalyService;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.service.impl.BigQueryDataResponseServiceImpl;
import io.harness.ccm.views.service.impl.CEViewFolderServiceImpl;
import io.harness.ccm.views.service.impl.CEViewServiceImpl;
import io.harness.ccm.views.service.impl.ClickHouseDataResponseServiceImpl;
import io.harness.ccm.views.service.impl.ClickHouseViewsBillingServiceImpl;
import io.harness.ccm.views.service.impl.GovernanceRuleServiceImpl;
import io.harness.ccm.views.service.impl.LabelFlattenedServiceImpl;
import io.harness.ccm.views.service.impl.PerspectiveAnomalyServiceImpl;
import io.harness.ccm.views.service.impl.RuleExecutionServiceImpl;
import io.harness.ccm.views.service.impl.ViewCustomFieldServiceImpl;
import io.harness.ccm.views.service.impl.ViewsBillingServiceImpl;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.ff.FeatureFlagService;
import io.harness.ff.FeatureFlagServiceImpl;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.instanceng.InstanceNGResourceClientModule;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.lock.PersistentLocker;
import io.harness.lock.noop.PersistentNoopLocker;
import io.harness.metrics.modules.MetricsModule;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.mongo.MongoConfig;
import io.harness.notifications.NotificationResourceClientModule;
import io.harness.persistence.HPersistence;
import io.harness.pricing.client.CloudInfoPricingClientModule;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.morphia.BatchProcessingModuleRegistrars;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.SlackMessageSenderImpl;
import software.wings.service.impl.ce.CeAccountExpirationCheckerImpl;
import software.wings.service.impl.instance.CloudToHarnessMappingServiceImpl;
import software.wings.service.impl.instance.DeploymentServiceImpl;
import software.wings.service.impl.security.NoOpSecretManagerImpl;
import software.wings.service.intfc.SlackMessageSender;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchProcessingModule extends AbstractModule {
  BatchMainConfig batchMainConfig;

  BatchProcessingModule(BatchMainConfig batchMainConfig) {
    this.batchMainConfig = batchMainConfig;
  }

  /**
   * Required by io.harness.ccm.commons.utils.BigQueryHelper, though io.harness.ccm.commons.beans.config.GcpConfig is
   * utilized ce-nextgen application only.
   */
  @Provides
  @Singleton
  @Named("gcpConfig")
  public io.harness.ccm.commons.beans.config.GcpConfig gcpConfig() {
    return batchMainConfig.getGcpConfig();
  }

  @Provides
  @Singleton
  @Named("dbAliases")
  public List<String> getDbAliases() {
    return batchMainConfig.getDbAliases();
  }

  @Provides
  @Singleton
  @Named("clickHouseConfig")
  public ClickHouseConfig clickHouseConfig() {
    return batchMainConfig.getClickHouseConfig();
  }

  @Provides
  @Singleton
  @Named("isClickHouseEnabled")
  boolean isClickHouseEnabled() {
    return batchMainConfig.isClickHouseEnabled();
  }

  @Provides
  @Singleton
  @Named("governanceConfig")
  io.harness.remote.GovernanceConfig governanceConfig() {
    return batchMainConfig.getGovernanceConfig();
  }

  @Override
  protected void configure() {
    bind(SecretManager.class).to(NoOpSecretManagerImpl.class);
    bind(EncryptedSettingAttributes.class).to(NoOpSecretManagerImpl.class);
    bind(HPersistence.class).to(WingsMongoPersistence.class);
    bind(WingsPersistence.class).to(WingsMongoPersistence.class);
    bind(DeploymentService.class).to(DeploymentServiceImpl.class);
    bind(CloudToHarnessMappingService.class).to(CloudToHarnessMappingServiceImpl.class);
    bind(ProductMetricsService.class).to(ProductMetricsServiceImpl.class);
    bind(CESlackWebhookService.class).to(CESlackWebhookServiceImpl.class);
    bind(SlackMessageSender.class).to(SlackMessageSenderImpl.class);
    bind(BigQueryService.class).to(BigQueryServiceImpl.class);
    bind(CeCloudMetricsService.class).to(CeCloudMetricsServiceImpl.class);
    bind(CENGTelemetryService.class).to(CENGTelemetryServiceImpl.class);
    bind(CEViewService.class).to(CEViewServiceImpl.class);
    bind(CEViewFolderService.class).to(CEViewFolderServiceImpl.class);
    bind(ViewCustomFieldService.class).to(ViewCustomFieldServiceImpl.class);
    bind(BusinessMappingService.class).to(BusinessMappingServiceImpl.class);
    bind(BusinessMappingHistoryService.class).to(BusinessMappingHistoryServiceImpl.class);
    bind(BusinessMappingValidationService.class).to(BusinessMappingValidationServiceImpl.class);
    bind(CeAccountExpirationChecker.class).to(CeAccountExpirationCheckerImpl.class);
    bind(GovernanceRuleService.class).to(GovernanceRuleServiceImpl.class);
    bind(RuleExecutionService.class).to(RuleExecutionServiceImpl.class);
    bind(AnomalyService.class).to(AnomalyServiceImpl.class);
    bind(ManagedAccountService.class).to(ManagedAccountServiceImpl.class);
    bind(MarginDetailsService.class).to(MarginDetailsServiceImpl.class);
    bind(ManagedAccountDataService.class).to(ManagedAccountDataServiceImpl.class);
    bind(MarginDetailsBqService.class).to(MarginDetailsBqServiceImpl.class);
    bind(MspValidationService.class).to(MspValidationServiceImpl.class);
    install(new ConnectorResourceClientModule(batchMainConfig.getNgManagerServiceHttpClientConfig(),
        batchMainConfig.getNgManagerServiceSecret(), BATCH_PROCESSING.getServiceId(), ClientMode.PRIVILEGED));
    install(new InstanceNGResourceClientModule(batchMainConfig.getNgManagerServiceHttpClientConfig(),
        batchMainConfig.getNgManagerServiceSecret(), BATCH_PROCESSING.getServiceId(), ClientMode.PRIVILEGED));
    install(NgLicenseHttpClientModule.getInstance(batchMainConfig.getNgManagerServiceHttpClientConfig(),
        batchMainConfig.getNgManagerServiceSecret(), MANAGER.getServiceId()));
    install(new NotificationResourceClientModule(batchMainConfig.getCeNgServiceHttpClientConfig(),
        batchMainConfig.getCeNgServiceSecret(), BATCH_PROCESSING.getServiceId(), ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(batchMainConfig.getNgManagerServiceHttpClientConfig(),
        batchMainConfig.getNgManagerServiceSecret(), BATCH_PROCESSING.getServiceId()));
    install(new AzureVmPricingClientModule(batchMainConfig.getAzureVmPricingConfig()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        SegmentConfig segmentConfig = batchMainConfig.getSegmentConfig();
        return SegmentConfiguration.builder()
            .enabled(segmentConfig.isEnabled())
            .apiKey(segmentConfig.getApiKey())
            .url(segmentConfig.getUrl())
            .build();
      }
    });
    bind(InstanceDataService.class).to(InstanceDataServiceImpl.class);
    bind(ClusterRecordService.class).to(ClusterRecordServiceImpl.class);
    bind(RecommendationCrudService.class).to(RecommendationCrudServiceImpl.class);
    bind(ClusterHelper.class).to(ClusterHelperImpl.class);
    bind(BudgetCostService.class).to(BudgetCostServiceImpl.class);
    bind(EntityMetadataService.class).to(EntityMetadataServiceImpl.class);
    bind(BudgetService.class).to(BudgetServiceImpl.class);
    bind(PerspectiveAnomalyService.class).to(PerspectiveAnomalyServiceImpl.class);
    bind(CurrencyPreferenceService.class).to(CurrencyPreferenceServiceImpl.class);
    bind(CurrencyPreferenceHelper.class).to(CurrencyPreferenceHelperImpl.class);
    bind(CCMJiraHelper.class).to(CCMJiraHelperImpl.class);
    bind(ClickHouseService.class).to(ClickHouseServiceImpl.class);
    bind(BudgetGroupService.class).to(BudgetGroupServiceImpl.class);
    bind(LabelFlattenedService.class).to(LabelFlattenedServiceImpl.class);

    install(new MetricsModule());
    install(new CENGGraphQLModule(batchMainConfig.getCurrencyPreferencesConfig()));
    bind(MetricsPublisher.class).to(BatchProcessingMetricsPublisher.class).in(Scopes.SINGLETON);
    install(new AccountClientModule(batchMainConfig.getManagerServiceHttpClientConfig(),
        batchMainConfig.getNgManagerServiceSecret(), BATCH_PROCESSING.getServiceId()));

    if (batchMainConfig.isClickHouseEnabled()) {
      bind(ViewsBillingService.class).to(ClickHouseViewsBillingServiceImpl.class);
      bind(DataResponseService.class).to(ClickHouseDataResponseServiceImpl.class);
    } else {
      bind(ViewsBillingService.class).to(ViewsBillingServiceImpl.class);
      bind(DataResponseService.class).to(BigQueryDataResponseServiceImpl.class);
    }

    bindPricingServices();

    bindCFServices();

    bindRetryOnExceptionInterceptor();
    bind(AWSOrganizationHelperService.class).to(AWSOrganizationHelperServiceImpl.class);
    bind(CCMServiceNowHelper.class).to(CCMServiceNowHelperImpl.class);
  }

  private void bindPricingServices() {
    final BanzaiConfig banzaiConfig = batchMainConfig.getBanzaiConfig();
    final String pricingServiceUrl = String.format("%s:%s/", banzaiConfig.getHost(), banzaiConfig.getPort());
    final ServiceHttpClientConfig httpClientConfig = ServiceHttpClientConfig.builder()
                                                         .baseUrl(pricingServiceUrl)
                                                         .connectTimeOutSeconds(120)
                                                         .readTimeOutSeconds(120)
                                                         .build();

    install(new CloudInfoPricingClientModule(httpClientConfig));
  }

  /**
   * This dependency only exists for the CFMigrationService which BatchProcessing will never use. However,
   * since it is sharing the same module, we have to provide an implementation for the same. Hence we are using
   * NOOP over here
   */
  private void bindCFServices() {
    ExecutorModule.getInstance().setExecutorService(Executors.newCachedThreadPool());
    install(ExecutorModule.getInstance());
    install(TimeModule.getInstance());

    bind(PersistentLocker.class).to(PersistentNoopLocker.class).in(Scopes.SINGLETON);
    OptionalBinder.newOptionalBinder(binder(), AccountClient.class);
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
  }

  private void bindRetryOnExceptionInterceptor() {
    bind(MethodExecutionHelper.class); // untargetted binding for eager loading
    ProviderMethodInterceptor retryOnExceptionInterceptor =
        new ProviderMethodInterceptor(getProvider(RetryOnExceptionInterceptor.class));
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(RetryOnException.class), retryOnExceptionInterceptor);
  }

  @Provides
  @Singleton
  MongoConfig mongoConfig(BatchMainConfig batchMainConfig) {
    return batchMainConfig.getHarnessMongo();
  }

  @Provides
  @Singleton
  List<YamlSchemaRootClass> yamlSchemaRootClasses() {
    return ImmutableList.<YamlSchemaRootClass>builder()
        .addAll(BatchProcessingModuleRegistrars.yamlSchemaRegistrars)
        .build();
  }
}
