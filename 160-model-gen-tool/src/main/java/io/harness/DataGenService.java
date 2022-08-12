/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.SeedData.randomText;
import static io.harness.SeedData.seedNames;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.FileBucket.PLATFORMS;
import static io.harness.generator.AccountGenerator.Accounts;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.shell.AccessType.KEY;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.AppContainer.Builder.anAppContainer;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.HostConnectionAttributes.ConnectionType.SSH;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SystemCatalog.Builder.aSystemCatalog;
import static software.wings.beans.SystemCatalog.CatalogType.APPSTACK;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.sm.StateType.STACK_DRIVER_LOG;
import static software.wings.utils.ContainerFamily.TOMCAT;
import static software.wings.utils.UsageRestrictionsUtils.getAllAppAllEnvUsageRestrictions;

import static java.util.Arrays.asList;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SecretText;
import io.harness.configuration.DeployMode;
import io.harness.entity.ServiceSecretKey;
import io.harness.entity.ServiceSecretKey.ServiceSecretKeyKeys;
import io.harness.entity.ServiceSecretKey.ServiceType;
import io.harness.ff.FeatureFlagService;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.DelegateProfileGenerator;
import io.harness.generator.DelegateRingGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureProvisionerGenerator;
import io.harness.generator.LicenseGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.PipelineGenerator;
import io.harness.generator.PipelineGenerator.Pipelines;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ResourceConstraintGenerator;
import io.harness.generator.ResourceConstraintGenerator.ResourceConstraints;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.ServiceGuardGenerator;
import io.harness.generator.SettingGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.generator.WorkflowGenerator.Workflows;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.manage.GlobalContextManager;
import io.harness.mongo.IndexManager;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AppContainer;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SystemCatalog;
import software.wings.beans.Workflow;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.dl.WingsPersistence;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.SystemCatalogService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.Morphia;

@Singleton
@Slf4j
public class DataGenService {
  private static final String APACHE_TOMCAT_7_0_78_TAR_GZ = "apache-tomcat-7.0.78.tar.gz";
  private static final String NEW_RELIC_CONNECTOR_NAME = "NewRelic";
  private static final int NUM_APPS = 1; /* Max 1000 */
  private static final int NUM_SERVICES_PER_APP = 1; /* Max 1000 */
  public static final String AWS_PLAY_GROUND = "aws-playground";
  public static final String WINGS_KEY = "Wings Key";

  private List<String> appNames = new ArrayList<>(seedNames);
  private List<String> serviceNames;

  @Inject private Morphia morphia;
  @Inject @Named("primaryDatastore") private AdvancedDatastore primaryDatastore;

  @Inject private WorkflowService workflowService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private MainConfiguration configuration;
  @Inject private SystemCatalogService systemCatalogService;
  @Inject private SettingsService settingsService;
  @Inject private EnvironmentService environmentService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private AccountService accountService;
  @Inject private UserGroupService userGroupService;
  @Inject private UserService userService;

  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private ServiceGuardGenerator serviceGuardGenerator;
  @Inject private InfrastructureProvisionerGenerator infrastructureProvisionerGenerator;
  @Inject private LicenseGenerator licenseGenerator;
  @Inject private ResourceConstraintGenerator resourceConstraintGenerator;
  @Inject private PipelineGenerator pipelineGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private ScmSecret scmSecret;
  @Inject private AppContainerService appContainerService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LoginSettingsService loginSettingsService;
  @Inject private IndexManager indexManager;
  @Inject private SecretManager secretManager;
  @Inject private DelegateProfileGenerator delegateProfileGenerator;
  @Inject private DelegateRingGenerator delegateRingGenerator;

  public void populateData() {
    dropDBAndEnsureIndexes();
    templateGalleryService.loadHarnessGallery();
    templateGalleryService.copyHarnessTemplates();

    {
      Seed seed = new Seed(0);
      accountGenerator.ensurePredefined(seed, ownerManager.create(), Accounts.RBAC_TEST);
    }

    Seed seed = new Seed(0);

    accountGenerator.ensurePredefined(seed, ownerManager.create(), Accounts.HARNESS_TEST);

    Owners owners = ownerManager.create();
    Account account = accountGenerator.ensurePredefined(seed, owners, Accounts.GENERIC_TEST);
    delegateProfileGenerator.ensureAllPredefined(seed, owners);
    delegateRingGenerator.createAllRings();
    createGlobalSettings(account);
    createApps(account);
    createTestApplication(account);

    loadAppStackCatalogs();
  }

  private void createApps(Account account) {
    List<Application> apps = createApplications(account.getUuid());

    try {
      for (Application application : apps) {
        addServices(application.getAppId());
      }
    } catch (Exception ex) {
      log.error(ex.getMessage());
    }

    featureFlagService.initializeFeatureFlags(DeployMode.AWS, "");
    createServiceSecretKey(ServiceType.LEARNING_ENGINE, "67d9b94d9856665afc21acd3aa745401");
  }

  private void createServiceSecretKey(ServiceType serviceType, String secret) {
    wingsPersistence.findAndModify(
        wingsPersistence.createQuery(ServiceSecretKey.class).filter(ServiceSecretKeyKeys.serviceType, serviceType),
        wingsPersistence.createUpdateOperations(ServiceSecretKey.class).set(ServiceSecretKeyKeys.serviceSecret, secret),
        new FindAndModifyOptions().upsert(true));
  }

  protected void dropDBAndEnsureIndexes() {
    wingsPersistence.getDatastore(DEFAULT_STORE).getDB().dropDatabase();
    indexManager.ensureIndexes(AUTO, primaryDatastore, morphia, null);
  }

  private void createGlobalSettings(Account account) {
    Seed seed = new Seed(0);
    String accountId = account.getUuid();
    Owners owners = new Owners();
    owners.add(account);

    settingGenerator.ensureAllPredefined(seed, owners);

    UsageRestrictions defaultUsageRestrictions = getAllAppAllEnvUsageRestrictions();

    String splunkPwd = secretManager.saveSecretText(accountId,
        SecretText.builder()
            .name("splunk_pwd")
            .value(scmSecret.decryptToString(new SecretName("splunk_config_password")))
            .usageRestrictions(defaultUsageRestrictions)
            .build(),
        false);

    SettingAttribute splunkSettingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName("Splunk")
            .withAccountId(accountId)
            .withValue(SplunkConfig.builder()
                           .accountId(accountId)
                           .splunkUrl("https://ec2-52-54-103-49.compute-1.amazonaws.com:8089")
                           .encryptedPassword(splunkPwd)
                           .username("admin")
                           .build())
            .withUsageRestrictions(defaultUsageRestrictions)
            .build();
    wingsPersistence.save(splunkSettingAttribute);

    String appdPwd = secretManager.saveSecretText(accountId,
        SecretText.builder()
            .name("appd_test_pwd")
            .value(scmSecret.decryptToString(new SecretName("appd_config_password")))
            .usageRestrictions(defaultUsageRestrictions)
            .build(),
        false);

    SettingAttribute appdSettingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CONNECTOR)
            .withName("AppDynamics")
            .withAccountId(accountId)
            .withValue(AppDynamicsConfig.builder()
                           .accountId(accountId)
                           .controllerUrl("https://harness-test.saas.appdynamics.com/controller")
                           .username("raghu@harness.io")
                           .accountname("harness-test")
                           .encryptedPassword(appdPwd)
                           .build())
            .withUsageRestrictions(defaultUsageRestrictions)
            .build();
    wingsPersistence.save(appdSettingAttribute);

    String newRelicApiKey = secretManager.saveSecretText(accountId,
        SecretText.builder()
            .name("new_relic_api_key")
            .value(scmSecret.decryptToString(new SecretName("new_relic_api_key")))
            .usageRestrictions(defaultUsageRestrictions)
            .build(),
        false);

    SettingAttribute newRelicSettingAttribute = aSettingAttribute()
                                                    .withCategory(SettingCategory.CONNECTOR)
                                                    .withName(NEW_RELIC_CONNECTOR_NAME)
                                                    .withAccountId(accountId)
                                                    .withValue(NewRelicConfig.builder()
                                                                   .accountId(accountId)
                                                                   .newRelicUrl("https://api.newrelic.com")
                                                                   .encryptedApiKey(newRelicApiKey)
                                                                   .build())
                                                    .withUsageRestrictions(defaultUsageRestrictions)
                                                    .build();

    wingsPersistence.save(newRelicSettingAttribute);

    SettingAttribute awsNonProdAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withName(AWS_PLAY_GROUND)
            .withAppId(GLOBAL_APP_ID)
            .withEnvId(GLOBAL_ENV_ID)
            .withAccountId(accountId)
            .withValue(AwsConfig.builder()
                           .accessKey(scmSecret.decryptToCharArray(new SecretName("aws_playground_access_key")))
                           .secretKey(scmSecret.decryptToCharArray(new SecretName("aws_playground_secret_key")))
                           .accountId(accountId)
                           .build())
            .withUsageRestrictions(defaultUsageRestrictions)
            .build();
    wingsPersistence.save(awsNonProdAttribute);

    SettingAttribute hostConnection =
        aSettingAttribute()
            .withAccountId(accountId)
            .withAppId(GLOBAL_APP_ID)
            .withEnvId(GLOBAL_ENV_ID)
            .withName(WINGS_KEY)
            .withValue(aHostConnectionAttributes()
                           .withConnectionType(SSH)
                           .withAccessType(KEY)
                           .withAccountId(accountId)
                           .withUserName("ubuntu")
                           .withKey(scmSecret.decryptToCharArray(new SecretName("ubuntu_ssh_key")))
                           .build())
            .withUsageRestrictions(defaultUsageRestrictions)
            .build();
    wingsPersistence.save(hostConnection);

    loginSettingsService.createDefaultLoginSettings(account);
  }

  private void createTestApplication(Account account) {
    Seed seed = new Seed(0);

    Owners owners = ownerManager.create();

    owners.obtainEnvironment(() -> environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST));

    owners.obtainService(() -> serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST));

    resourceConstraintGenerator.ensurePredefined(seed, owners, ResourceConstraints.GENERIC_ASAP_TEST);
    resourceConstraintGenerator.ensurePredefined(seed, owners, ResourceConstraints.GENERIC_FIFO_TEST);

    pipelineGenerator.ensurePredefined(seed, owners, Pipelines.RESOURCE_CONSTRAINT_WORKFLOW);
    workflowGenerator.ensurePredefined(seed, owners, Workflows.PERMANENTLY_BLOCKED_RESOURCE_CONSTRAINT);

    Workflow workflow1 = workflowGenerator.ensurePredefined(seed, owners, Workflows.BASIC_SIMPLE);

    serviceGuardGenerator.ensurePredefined(seed, owners, STACK_DRIVER_LOG);

    Workflow workflow2 = workflowGenerator.ensurePredefined(seed, owners, Workflows.BASIC_10_NODES);

    Workflow workflow3 = workflowGenerator.ensurePredefined(seed, owners, Workflows.ROLLING_10_NODES);

    pipelineGenerator.ensurePredefined(seed, owners, Pipelines.BARRIER);

    pipelineGenerator.ensurePipeline(seed, owners,
        Pipeline.builder()
            .appId(workflow1.getAppId())
            .name("Pipeline")
            .pipelineStages(
                asList(PipelineStage.builder()
                           .pipelineStageElements(asList(PipelineStageElement.builder()
                                                             .name("Simple")
                                                             .type(ENV_STATE.name())
                                                             .properties(ImmutableMap.of("envId", workflow1.getEnvId(),
                                                                 "workflowId", workflow1.getUuid()))
                                                             .build()))
                           .build(),
                    PipelineStage.builder()
                        .pipelineStageElements(asList(PipelineStageElement.builder()
                                                          .name("10 nodes")
                                                          .type(ENV_STATE.name())
                                                          .properties(ImmutableMap.of("envId", workflow2.getEnvId(),
                                                              "workflowId", workflow2.getUuid()))
                                                          .build()))
                        .build(),
                    PipelineStage.builder()
                        .pipelineStageElements(
                            asList(PipelineStageElement.builder()
                                       .name("10 rolling nodes")
                                       .type(ENV_STATE.name())
                                       .properties(ImmutableMap.of(
                                           "envId", workflow3.getEnvId(), "workflowId", workflow3.getUuid()))
                                       .build()))
                        .build()))
            .build());

    Owners terraformOwners = ownerManager.create();
    workflowGenerator.ensurePredefined(seed, terraformOwners, Workflows.TERRAFORM);
  }

  private List<Application> createApplications(String accountId) {
    List<Application> apps = new ArrayList<>();
    for (int i = 0; i < NUM_APPS; i++) {
      String name = getName(appNames);
      Seed seed = new Seed(0);
      Owners owners = ownerManager.create();

      Application application =
          applicationGenerator.ensureApplication(seed, owners, anApplication().accountId(accountId).name(name).build());
      apps.add(application);
    }
    return apps;
  }

  private List<Service> addServices(String appId) throws IOException {
    serviceNames = new ArrayList<>(seedNames);
    List<Service> services = new ArrayList<>();

    for (int i = 0; i < NUM_SERVICES_PER_APP; i++) {
      String name = getName(serviceNames);
      Seed seed = new Seed(0);
      Owners owners = ownerManager.create();
      Service service = serviceGenerator.ensureService(seed, owners,
          Service.builder().name(name).description(randomText(40)).appId(appId).artifactType(ArtifactType.WAR).build());
      services.add(service);
    }
    return services;
  }

  private String getName(List<String> names) {
    int nameIdx = 0;
    String name = names.get(nameIdx);
    names.remove(nameIdx);
    return name;
  }

  private static final String AWS_S3_CATALOG_TOMCAT7 =
      "https://s3.amazonaws.com/harness-catalogs/appstack/apache-tomcat-7.0.78.tar.gz";

  /**
   * Loads default application containers
   */
  public void loadAppStackCatalogs() {
    try {
      createOrUpdateSystemAppStackCatalogs();
      createOrUpdateSystemAppContainers();
    } catch (Exception e) {
      log.error("Failed to load app stack catalogs", e);
    }
  }

  public void createOrUpdateSystemAppStackCatalogs() {
    long fileSize = configuration.getFileUploadLimits().getAppContainerLimit();

    List<SystemCatalog> systemCatalogs = systemCatalogService.list(aPageRequest()
                                                                       .addFilter("catalogType", EQ, APPSTACK)
                                                                       .addFilter("family", EQ, TOMCAT)
                                                                       .addFilter("appId", EQ, GLOBAL_APP_ID)
                                                                       .build());

    Map<String, SystemCatalog> fileToSystemCatalog =
        systemCatalogs.stream().collect(Collectors.toMap(SystemCatalog::getFileName, Function.identity()));
    log.info("Creating System App Stack Catalogs");
    // Create Tomcat 7 Standard
    SystemCatalog systemCatalog;
    if (!fileToSystemCatalog.containsKey(APACHE_TOMCAT_7_0_78_TAR_GZ)) {
      systemCatalog = aSystemCatalog()
                          .withCatalogType(APPSTACK)
                          .withName("Standard Tomcat 7")
                          .withFileName(APACHE_TOMCAT_7_0_78_TAR_GZ)
                          .withAppId(GLOBAL_APP_ID)
                          .withFamily(TOMCAT)
                          .withNotes("System created.")
                          .withVersion("7.0.78")
                          .build();
      systemCatalog.setAccountId(GLOBAL_ACCOUNT_ID);
      systemCatalogService.save(systemCatalog, AWS_S3_CATALOG_TOMCAT7, PLATFORMS, fileSize);
    } else {
      // call update --> Support the update
      systemCatalog = fileToSystemCatalog.get(APACHE_TOMCAT_7_0_78_TAR_GZ);
      systemCatalog.setVersion("7.0.78");
      systemCatalogService.update(systemCatalog, AWS_S3_CATALOG_TOMCAT7, PLATFORMS, fileSize);
    }
  }

  public void createOrUpdateSystemAppContainers() {
    log.info("Creating System App Containers");
    List<Account> accounts =
        accountService.list(aPageRequest().withLimit(PageRequest.UNLIMITED).addFieldsIncluded("uuid").build());
    if (isEmpty(accounts)) {
      return;
    }
    List<SystemCatalog> systemCatalogs = systemCatalogService.list(aPageRequest()
                                                                       .addFilter("catalogType", EQ, APPSTACK)
                                                                       .addFilter("family", EQ, TOMCAT)
                                                                       .addFilter("appId", EQ, GLOBAL_APP_ID)
                                                                       .build());
    accounts.forEach(account -> {
      for (SystemCatalog systemCatalog : systemCatalogs) {
        AppContainer appContainer = anAppContainer()
                                        .withAccountId(account.getUuid())
                                        .withAppId(systemCatalog.getAppId())
                                        .withChecksum(systemCatalog.getChecksum())
                                        .withChecksumType(systemCatalog.getChecksumType())
                                        .withFamily(systemCatalog.getFamily())
                                        .withStackRootDirectory(systemCatalog.getStackRootDirectory())
                                        .withFileName(systemCatalog.getFileName())
                                        .withFileUuid(systemCatalog.getFileUuid())
                                        .withFileType(systemCatalog.getFileType())
                                        .withSize(systemCatalog.getSize())
                                        .withName(systemCatalog.getName())
                                        .withSystemCreated(true)
                                        .withDescription(systemCatalog.getNotes())
                                        .withHardened(systemCatalog.isHardened())
                                        .withVersion(systemCatalog.getVersion())
                                        .build();
        try {
          PageResponse<AppContainer> pageResponse =
              appContainerService.list(aPageRequest()
                                           .addFilter("accountId", EQ, account.getUuid())
                                           .addFilter("fileUuid", EQ, systemCatalog.getFileUuid())
                                           .build());
          if (isEmpty(pageResponse.getResponse())) {
            appContainerService.save(appContainer);
          } else {
            AppContainer storedAppContainer = pageResponse.getResponse().get(0);
            storedAppContainer.setVersion(systemCatalog.getVersion());
            storedAppContainer.setHardened(systemCatalog.isHardened());
            storedAppContainer.setDescription(systemCatalog.getNotes());
            storedAppContainer.setFileName(systemCatalog.getFileName());
            storedAppContainer.setStackRootDirectory(systemCatalog.getStackRootDirectory());
            appContainerService.update(storedAppContainer);
          }
        } catch (Exception e) {
          log.error("", e);
          log.info("Error while creating system app container " + appContainer);
        }
      }
    });
    log.info("System App Containers created successfully");
  }

  /**
   * Please do not remove the main class as UI test needs datagen as Jar
   * @param args
   */
  public static void main(String[] args) {
    log.info("Running tests!");

    try (GlobalContextManager.GlobalContextGuard globalContextGuard =
             GlobalContextManager.initGlobalContextGuard(null)) {
      new DataGenService().populateData();
    }
  }
}
