/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.generator.ServiceGenerator.Services.KUBERNETES_GENERIC_TEST;
import static io.harness.generator.SettingGenerator.Settings.ECS_FUNCTIONAL_TEST_GIT_ACCOUNT;
import static io.harness.generator.SettingGenerator.Settings.ECS_FUNCTIONAL_TEST_GIT_REPO;
import static io.harness.generator.SettingGenerator.Settings.PCF_FUNCTIONAL_TEST_GIT_REPO;
import static io.harness.govern.Switch.unhandled;

import static software.wings.api.DeploymentType.AZURE_WEBAPP;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.Service.ServiceBuilder;
import static software.wings.beans.Service.builder;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SettingGenerator.Settings;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.generator.artifactstream.ArtifactStreamManager.ArtifactStreams;
import io.harness.k8s.model.HelmVersion;
import io.harness.manifest.CustomSourceConfig;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.LambdaSpecification;
import software.wings.beans.LambdaSpecification.DefaultSpecification;
import software.wings.beans.LambdaSpecification.FunctionSpecification;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.utils.ArtifactType;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.benas.randombeans.api.EnhancedRandom;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ServiceGenerator {
  private static final String HELM_S3_SERVICE_NAME = "Helm S3 Functional Test";
  private static final String CHARTMUSEUM_CHART_NAME = "chartmuseum";
  private static final String BASE_PATH = "helm/charts";
  private static final String K8S_CUSTOM_SCRIPT_PATH = "k8s-manifests/custom/custom-manifest-script.sh";
  private static final String HELM_CUSTOM_SCRIPT_PATH = "k8s-manifests/custom/helm-custom-manifest-script.sh";
  private static final String HELM_CUSTOM_SCRIPT_PATH_VALUE_OVERRIDE =
      "k8s-manifests/custom/helm-custom-manifest-no-value-script.sh";
  private static final String HELM_CUSTOM_SCRIPT_PATH_MULTIPLE_VALUE_OVERRIDE =
      "k8s-manifests/custom/helm-custom-manifest-multiple-value-script.sh";
  private static final String K8S_CUSTOM_SCRIPT_NO_VALUES_PATH = "k8s-manifests/custom/no-values-manifest-script.sh";
  private static final String OPENSHIFT_CUSTOM_SCRIPT_PATH = "k8s-manifests/custom/custom-openshift-manifest-script.sh";
  private static final String K8S_CUSTOM_MANIFEST_PATH = "${serviceVariable.manifestPath}/";
  private static final String K8S_CUSTOM_MANIFEST_ABSOLUTE_PATH =
      "${serviceVariable.absolutePath}/${serviceVariable.manifestPath}";
  private static final String OPENSHIFT_CUSTOM_MANIFEST_PATH = "${serviceVariable.manifestPath}/template.yaml";
  private static final String OPENSHIFT_CUSTOM_MANIFEST_ABSOLUTE_PATH =
      "${serviceVariable.absolutePath}/${serviceVariable.manifestPath}/template.yaml";

  @Inject private OwnerManager ownerManager;
  @Inject ApplicationGenerator applicationGenerator;
  @Inject ArtifactStreamManager artifactStreamManager;
  @Inject SettingGenerator settingGenerator;
  @Inject ServiceResourceService serviceResourceService;
  @Inject WingsPersistence wingsPersistence;
  @Inject ApplicationManifestService applicationManifestService;
  @Inject ServiceVariableService serviceVariableService;
  @Inject SecretGenerator secretGenerator;

  public enum Services {
    GENERIC_TEST,
    KUBERNETES_GENERIC_TEST,
    FUNCTIONAL_TEST,
    WINDOWS_TEST,
    ECS_TEST,
    K8S_V2_TEST,
    K8_V2_SKIP_VERSIONING_TEST,
    K8S_V2_LIST_TEST,
    CUSTOM_MANIFEST_K8S_V2_TEST,
    CUSTOM_MANIFEST_K8S_V2_ABSOLUTE_PATH_TEST,
    CUSTOM_MANIFEST_K8S_V2_NO_VALUES_TEST,
    CUSTOM_MANIFEST_K8S_V2_VALUES_OVERRIDES,
    CUSTOM_MANIFEST_OPENSHIFT_TEST,
    CUSTOM_MANIFEST_OPENSHIFT_TEST_ABSOLUTE_PATH_TEST,
    CUSTOM_MANFIEST_OPENSHIFT_PARAMS_OVERRIDES,
    CUSTOM_MANIFEST_HELM_S3,
    CUSTOM_MANIFEST_HELM_VALUE_OVERRIDE,
    CUSTOM_MANIFEST_HELM_MULTIPLE_OVERRIDE,
    MULTI_ARTIFACT_FUNCTIONAL_TEST,
    MULTI_ARTIFACT_K8S_V2_TEST,
    NAS_FUNCTIONAL_TEST,
    PCF_V2_TEST,
    PCF_V2_REMOTE_TEST,
    HELM_S3,
    WINDOWS_TEST_DOWNLOAD,
    ARTIFACTORY_GENERIC_TEST
  }

  public Service ensurePredefined(Randomizer.Seed seed, Owners owners, Services predefined) {
    switch (predefined) {
      case GENERIC_TEST:
        return ensureGenericTest(seed, owners, "Test Service");
      case FUNCTIONAL_TEST:
        return ensureFunctionalTest(seed, owners, "Functional Test Service");
      case ARTIFACTORY_GENERIC_TEST:
        return ensureGenericArtifactoryTest(seed, owners, "Artifactory Test Service");
      case KUBERNETES_GENERIC_TEST:
        return ensureKubernetesGenericTest(seed, owners);
      case WINDOWS_TEST:
        return ensureWindowsTest(seed, owners, "Test IIS APP Service");
      case K8S_V2_TEST:
        return ensureK8sTest(seed, owners, "Test K8sV2 Service");
      case K8_V2_SKIP_VERSIONING_TEST:
        return ensureK8sTestSkipVersioningAllObjects(seed, owners, "Test K8sV2 Service Skip Versioning");
      case K8S_V2_LIST_TEST:
        return ensureK8sListTest(seed, owners, "Test K8sV2 List Service");
      case CUSTOM_MANIFEST_K8S_V2_TEST:
        return ensureCustomManifestK8sTest(
            seed, owners, "Test K8sV2 Custom Manifest", K8S_CUSTOM_MANIFEST_PATH, K8S_CUSTOM_SCRIPT_PATH);
      case CUSTOM_MANIFEST_K8S_V2_ABSOLUTE_PATH_TEST:
        return ensureCustomManifestK8sTest(
            seed, owners, "Test K8sV2 Custom Manifest", K8S_CUSTOM_MANIFEST_ABSOLUTE_PATH, K8S_CUSTOM_SCRIPT_PATH);
      case CUSTOM_MANIFEST_K8S_V2_NO_VALUES_TEST:
        return ensureCustomManifestK8sTest(seed, owners, "Test K8sV2 No Values YAML Custom Manifest",
            K8S_CUSTOM_MANIFEST_PATH, K8S_CUSTOM_SCRIPT_NO_VALUES_PATH);
      case CUSTOM_MANIFEST_K8S_V2_VALUES_OVERRIDES:
        return ensureCustomManifestK8sValuesOverridesTest(seed, owners, "Test K8sV2 Values Overrides Custom Manifest");
      case CUSTOM_MANIFEST_OPENSHIFT_TEST:
        return ensureCustomManifestOpenshiftTest(
            seed, owners, "Test Openshift Custom Manifest", OPENSHIFT_CUSTOM_MANIFEST_PATH);
      case CUSTOM_MANIFEST_OPENSHIFT_TEST_ABSOLUTE_PATH_TEST:
        return ensureCustomManifestOpenshiftTest(
            seed, owners, "Test Openshift Custom Manifest", OPENSHIFT_CUSTOM_MANIFEST_ABSOLUTE_PATH);
      case CUSTOM_MANFIEST_OPENSHIFT_PARAMS_OVERRIDES:
        return ensureCustomManifestOpenshiftParamsOverrideTest(
            seed, owners, "Test Openshift Params Override", "${serviceVariable.manifestPath}/template.yaml");
      case CUSTOM_MANIFEST_HELM_S3:
        return ensureCustomManifestHelmS3Service(
            seed, owners, "Test Helm Custom Manifest", K8S_CUSTOM_MANIFEST_PATH, HELM_CUSTOM_SCRIPT_PATH);
      case CUSTOM_MANIFEST_HELM_VALUE_OVERRIDE:
        return ensureCustomManifestHelmValuesOverrides(seed, owners, "Test Helm Custom Manifest Value Override",
            K8S_CUSTOM_MANIFEST_PATH, HELM_CUSTOM_SCRIPT_PATH_VALUE_OVERRIDE);
      case CUSTOM_MANIFEST_HELM_MULTIPLE_OVERRIDE:
        return ensureCustomManifestHelmMultipleOverride(seed, owners, "Test Helm Custom Manifest Multiple Override",
            K8S_CUSTOM_MANIFEST_PATH, HELM_CUSTOM_SCRIPT_PATH_MULTIPLE_VALUE_OVERRIDE);
      case MULTI_ARTIFACT_FUNCTIONAL_TEST:
        return ensureMultiArtifactFunctionalTest(seed, owners, "MA-FunctionalTest Service");
      case MULTI_ARTIFACT_K8S_V2_TEST:
        return ensureMultiArtifactK8sTest(seed, owners, "MA-Test K8sV2 Service");
      case NAS_FUNCTIONAL_TEST:
        return ensureNasFunctionalTest(seed, owners, "Test NAS Service");
      case PCF_V2_TEST:
        return ensurePcfTest(seed, owners, "PCF Service");
      case PCF_V2_REMOTE_TEST:
        return ensurePcfTestRemote(seed, owners, "PCF Remote");
      case HELM_S3:
        return ensureHelmS3Service(seed, owners);
      default:
        unhandled(predefined);
    }

    return null;
  }

  public Service ensurePredefinedCustomDeployment(
      Randomizer.Seed seed, Owners owners, String templateUuid, String serviceName) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder()
            .name(serviceName)
            .deploymentType(DeploymentType.CUSTOM)
            .deploymentTypeTemplateId(templateUuid)
            .artifactType(ArtifactType.DOCKER)
            .build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensurePredefined(
      Randomizer.Seed seed, Owners owners, Services predefined, ArtifactStreams artifactStreams) {
    if (predefined == Services.WINDOWS_TEST_DOWNLOAD) {
      return ensureWindowsTest(seed, owners, "Test IIS APP Service Download", artifactStreams);
    } else {
      unhandled(predefined);
    }
    return null;
  }

  private Service ensureHelmS3Service(Seed seed, Owners owners) {
    Service service = ensureBasicHelmS3Service(seed, owners, HELM_S3_SERVICE_NAME);
    addApplicationManifestToService(seed, owners, service);
    return service;
  }

  private void addApplicationManifestToService(Seed seed, Owners owners, Service service) {
    SettingAttribute helmS3Connector = settingGenerator.ensurePredefined(seed, owners, Settings.HELM_S3_CONNECTOR);

    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(service.getUuid())
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .helmChartConfig(HelmChartConfig.builder()
                                                                       .connectorId(helmS3Connector.getUuid())
                                                                       .chartName(CHARTMUSEUM_CHART_NAME)
                                                                       .basePath(BASE_PATH)
                                                                       .build())
                                                  .kind(AppManifestKind.K8S_MANIFEST)
                                                  .build();
    applicationManifest.setAppId(service.getAppId());

    List<ApplicationManifest> applicationManifests =
        applicationManifestService.listAppManifests(service.getAppId(), service.getUuid());

    if (isEmpty(applicationManifests)) {
      applicationManifestService.create(applicationManifest);
    } else {
      boolean found = false;
      for (ApplicationManifest savedApplicationManifest : applicationManifests) {
        if (savedApplicationManifest.getKind() == AppManifestKind.K8S_MANIFEST
            && savedApplicationManifest.getStoreType() == StoreType.HelmChartRepo) {
          applicationManifest.setUuid(savedApplicationManifest.getUuid());
          applicationManifestService.update(applicationManifest);
          found = true;
          break;
        }
      }
      if (!found) {
        applicationManifestService.create(applicationManifest);
      }
    }
  }

  private Service ensureService(Service service) {
    Service existingService = exists(service);
    if (existingService != null) {
      return existingService;
    }
    return serviceResourceService.save(service);
  }

  private Service ensurePcfTest(Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder().name(name).artifactType(ArtifactType.PCF).deploymentType(DeploymentType.PCF).isPcfV2(true).build()));
    ArtifactStream artifactStream = artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.PCF);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  private Service ensurePcfTestRemote(Seed seed, Owners owners, String name) {
    final Service service = ensurePcfTest(seed, owners, name);
    final SettingAttribute gitConnectorSetting =
        settingGenerator.ensurePredefined(seed, owners, PCF_FUNCTIONAL_TEST_GIT_REPO);
    final ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                        .serviceId(service.getUuid())
                                                        .kind(AppManifestKind.K8S_MANIFEST)
                                                        .storeType(StoreType.Remote)
                                                        .gitFileConfig(GitFileConfig.builder()
                                                                           .connectorId(gitConnectorSetting.getUuid())
                                                                           .branch("master")
                                                                           .useBranch(true)
                                                                           .filePath("pcf-app1")
                                                                           .build())
                                                        .build();
    applicationManifest.setAppId(service.getAppId());
    upsertApplicationManifest(applicationManifest);

    return service;
  }

  private ApplicationManifest upsertApplicationManifest(ApplicationManifest applicationManifest) {
    final ApplicationManifest manifestByServiceId = applicationManifestService.getManifestByServiceId(
        applicationManifest.getAppId(), applicationManifest.getServiceId());
    if (manifestByServiceId != null) {
      applicationManifest.setUuid(manifestByServiceId.getUuid());
      return applicationManifestService.update(applicationManifest);
    }
    return applicationManifestService.create(applicationManifest);
  }

  public Service ensureWindowsTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.IIS_APP).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_IIS_APP_S3);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureWindowsTest(
      Randomizer.Seed seed, Owners owners, String name, ArtifactStreams artifactStreamPredefined) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.FUNCTIONAL_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.IIS_APP).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, artifactStreamPredefined, false, true);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureK8sTest(Randomizer.Seed seed, Owners owners, String name) {
    final Application application =
        owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .appId(application.getUuid())
            .build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureK8sTestSkipVersioningAllObjects(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();

    ApplicationManifest applicationManifest =
        applicationManifestService.getManifestByServiceId(service.getAppId(), service.getUuid());
    applicationManifest.setAppId(service.getAppId());
    applicationManifest.setSkipVersioningForAllK8sObjects(true);
    upsertApplicationManifest(applicationManifest);

    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureK8sListTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));

    addDeploymentListManifestFile(service);

    return service;
  }

  private void addDeploymentListManifestFile(Service service) {
    ApplicationManifest applicationManifest =
        applicationManifestService.getManifestByServiceId(service.getAppId(), service.getUuid());
    List<ManifestFile> manifestFiles =
        applicationManifestService.getManifestFilesByAppManifestId(service.getAppId(), applicationManifest.getUuid());
    for (ManifestFile manifestFile : manifestFiles) {
      if (!manifestFile.getFileName().equals("values.yaml")) {
        applicationManifestService.deleteManifestFileById(service.getAppId(), manifestFile.getUuid());
      }
    }

    URL url = ServiceGenerator.class.getClassLoader().getResource("k8s-manifests/deployment-list.yaml");
    String deploymentListYaml = null;
    try {
      deploymentListYaml = Resources.toString(url, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    ManifestFile listDeploymentSpec =
        ManifestFile.builder().fileName("templates/deployment.yaml").fileContent(deploymentListYaml).build();
    listDeploymentSpec.setAppId(service.getAppId());
    applicationManifestService.createManifestFileByServiceId(listDeploymentSpec, service.getUuid());
  }

  public Service ensureCustomManifestOpenshiftTest(
      Randomizer.Seed seed, Owners owners, String name, String manifestPath) {
    return ensureCustomManifestK8sV2Test(
        seed, owners, name, manifestPath, OPENSHIFT_CUSTOM_SCRIPT_PATH, StoreType.CUSTOM_OPENSHIFT_TEMPLATE);
  }

  public Service ensureCustomManifestOpenshiftParamsOverrideTest(
      Randomizer.Seed seed, Owners owners, String name, String manifestPath) {
    Service service = ensureCustomManifestK8sV2Test(
        seed, owners, name, manifestPath, OPENSHIFT_CUSTOM_SCRIPT_PATH, StoreType.CUSTOM_OPENSHIFT_TEMPLATE);
    ensureServiceVariableForService(service, "multipleOverridesPath",
        "${serviceVariable.overridesPath}/params1, ${serviceVariable.overridesPath}/params2");
    ensureCustomApplicationManifest(
        service, "", "${serviceVariable.multipleOverridesPath}", CUSTOM, AppManifestKind.OC_PARAMS);
    return service;
  }

  public Service ensureCustomManifestK8sTest(
      Randomizer.Seed seed, Owners owners, String name, String manifestPath, String scriptResource) {
    return ensureCustomManifestK8sV2Test(seed, owners, name, manifestPath, scriptResource, CUSTOM);
  }

  public Service ensureCustomManifestHelmS3Service(
      Seed seed, Owners owners, String name, String manifestPath, String scriptResource) {
    Service service = ensureBasicHelmS3Service(seed, owners, name);

    ensureCustomApplicationManifest(service, scriptResource, manifestPath, CUSTOM, AppManifestKind.K8S_MANIFEST);
    ensureServiceVariableForService(service, "manifestPath", "test-chart");
    ensureServiceVariableForService(service, "overridesPath1", "overrides/values1.yaml");
    ensureServiceVariableForService(service, "overridesPath2", "overrides/values2.yaml");
    secretGenerator.ensureSecretText(owners, "custom-manifest-fn-test-secret", "custom-manifest-fn-test-value");
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));

    return service;
  }

  public Service ensureCustomManifestHelmValuesOverrides(
      Seed seed, Owners owners, String name, String manifestPath, String scriptResource) {
    Service service = ensureBasicHelmS3Service(seed, owners, name);

    ensureServiceVariableForService(service, "manifestPath", "test-chart-without-value");
    ensureServiceVariableForService(service, "overridesPath1", "overrides/values1.yaml");
    ensureServiceVariableForService(service, "overrideDir", "overrides");

    ensureCustomApplicationManifest(service, scriptResource, manifestPath, CUSTOM, AppManifestKind.K8S_MANIFEST);
    ensureCustomApplicationManifest(service, "", "${serviceVariable.overridesPath1}", CUSTOM, AppManifestKind.VALUES);

    return service;
  }

  public Service ensureCustomManifestHelmMultipleOverride(
      Seed seed, Owners owners, String name, String manifestPath, String scriptResource) {
    Service service = ensureBasicHelmS3Service(seed, owners, name);

    ensureServiceVariableForService(service, "manifestPath", "test-chart-multiple-override");
    ensureServiceVariableForService(service, "overridesPath1", "multipleOverrides/values1.yaml");
    ensureServiceVariableForService(service, "overridesPath2", "multipleOverrides/values2.yaml");
    ensureServiceVariableForService(
        service, "multipleOverridePaths", "multipleOverrides/values2.yaml,multipleOverrides/values1.yaml");
    ensureServiceVariableForService(service, "overrideDir", "multipleOverrides");

    ensureCustomApplicationManifest(service, scriptResource, manifestPath, CUSTOM, AppManifestKind.K8S_MANIFEST);
    ensureCustomApplicationManifest(
        service, "", "${serviceVariable.multipleOverridePaths}", CUSTOM, AppManifestKind.VALUES);

    secretGenerator.ensureSecretText(owners, "custom-manifest-fn-test-secret", "custom-manifest-fn-test-value");
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));

    return service;
  }

  private Service ensureBasicHelmS3Service(Seed seed, Owners owners, String name) {
    Application application = owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, Applications.FUNCTIONAL_TEST));

    Service service = Service.builder()
                          .name(name)
                          .deploymentType(DeploymentType.HELM)
                          .appId(application.getUuid())
                          .artifactType(ArtifactType.DOCKER)
                          .helmVersion(HelmVersion.V3)
                          .build();
    ensureService(service);
    service = ensureService(seed, owners, service);
    owners.add(service);
    return service;
  }

  public Service ensureCustomManifestK8sValuesOverridesTest(Randomizer.Seed seed, Owners owners, String name) {
    Service service =
        ensureCustomManifestK8sV2Test(seed, owners, name, K8S_CUSTOM_MANIFEST_PATH, K8S_CUSTOM_SCRIPT_PATH, CUSTOM);
    ensureServiceVariableForService(service, "multipleOverridesPath",
        "${serviceVariable.overridesPath}/values1.yaml, ${serviceVariable.overridesPath}/values2.yaml");
    ensureCustomApplicationManifest(
        service, "", "${serviceVariable.multipleOverridesPath}", CUSTOM, AppManifestKind.VALUES);
    return service;
  }

  private Service ensureCustomManifestK8sV2Test(
      Randomizer.Seed seed, Owners owners, String name, String path, String scriptResource, StoreType storeType) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .build()));
    Service service = owners.obtainService();
    ensureCustomApplicationManifest(service, scriptResource, path, storeType, AppManifestKind.K8S_MANIFEST);
    ensureServiceVariableForService(
        service, "absolutePath", "/tmp/functional-tests/custom-manifest/${workflow.variables.workloadName}");
    ensureServiceVariableForService(service, "manifestPath", "custom/manifest");
    ensureServiceVariableForService(service, "overridesPath", "custom/overrides");
    secretGenerator.ensureSecretText(owners, "custom-manifest-fn-test-secret", "custom-manifest-fn-test-value");
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  private void ensureCustomApplicationManifest(
      Service service, String scriptResource, String path, StoreType storeType, AppManifestKind kind) {
    ApplicationManifest existing =
        applicationManifestService.getByServiceId(service.getAppId(), service.getUuid(), kind);
    URL url = isNotEmpty(scriptResource) ? ServiceGenerator.class.getClassLoader().getResource(scriptResource) : null;
    try {
      String customManifestScript = url != null ? Resources.toString(url, StandardCharsets.UTF_8) : "";
      ApplicationManifest applicationManifest =
          ApplicationManifest.builder()
              .kind(kind)
              .accountId(service.getAccountId())
              .storeType(storeType)
              .serviceId(service.getUuid())
              .customSourceConfig(CustomSourceConfig.builder().script(customManifestScript).path(path).build())
              .build();

      applicationManifest.setAppId(service.getAppId());
      if (existing != null) {
        applicationManifest.setUuid(existing.getUuid());
        applicationManifestService.update(applicationManifest);
      } else {
        applicationManifestService.create(applicationManifest);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private ServiceVariable ensureServiceVariableForService(Service service, String name, String value) {
    List<ServiceVariable> existing = serviceVariableService.getServiceVariablesForEntity(
        service.getAppId(), service.getUuid(), ServiceVariableService.EncryptedFieldMode.MASKED);
    for (ServiceVariable serviceVariable : existing) {
      if (serviceVariable.getName().equals(name)) {
        serviceVariable.setValue(value.toCharArray());
        return serviceVariableService.update(serviceVariable);
      }
    }

    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .envId(GLOBAL_ENV_ID)
                                          .entityId(service.getUuid())
                                          .entityType(EntityType.SERVICE)
                                          .type(ServiceVariableType.TEXT)
                                          .name(name)
                                          .value(value.toCharArray())
                                          .accountId(service.getAccountId())
                                          .build();
    serviceVariable.setAppId(service.getAppId());
    return serviceVariableService.save(serviceVariable);
  }

  public Service ensureEcsRemoteTest(Randomizer.Seed seed, Owners owners, String name, StoreType storeType,
      boolean accountConnector, String containerSpecFilePath, String serviceSpecFilePath) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder().name(name).artifactType(ArtifactType.DOCKER).deploymentType(DeploymentType.ECS).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));

    final ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                        .serviceId(service.getUuid())
                                                        .kind(AppManifestKind.K8S_MANIFEST)
                                                        .storeType(storeType)
                                                        .build();
    if (storeType == StoreType.Remote) {
      final SettingAttribute gitConnectorSetting = settingGenerator.ensurePredefined(
          seed, owners, accountConnector ? ECS_FUNCTIONAL_TEST_GIT_ACCOUNT : ECS_FUNCTIONAL_TEST_GIT_REPO);
      GitFileConfig gitFileConfig = GitFileConfig.builder()
                                        .connectorId(gitConnectorSetting.getUuid())
                                        .branch("ecs-gitops-test")
                                        .useBranch(true)
                                        .serviceSpecFilePath(serviceSpecFilePath)
                                        .taskSpecFilePath(containerSpecFilePath)
                                        .build();
      if (accountConnector) {
        gitFileConfig.setRepoName("arvind-test.git");
      }
      applicationManifest.setGitFileConfig(gitFileConfig);
    }

    applicationManifest.setAppId(service.getAppId());
    try {
      upsertApplicationManifest(applicationManifest);
    } catch (InvalidRequestException ex) {
      log.warn("Unable to save application manifest. Continuing", ex);
    }
    return service;
  }

  public Service ensureMultiArtifactK8sTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .build()));
    return owners.obtainService();
  }

  public Service ensureNasFunctionalTest(Randomizer.Seed seed, Owners owners, String name) {
    return ensureMultiArtifactFunctionalTest(seed, owners, name);
  }

  public Service ensureEcsTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    Service service = ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.DOCKER).build());
    owners.add(service);
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER, name);
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureAzureTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.WAR).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_AZURE);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(
        seed, owners, builder().name(name).artifactType(ArtifactType.WAR).deploymentType(DeploymentType.SSH).build()));
    ArtifactStream artifactStream = artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.AWS_AMI);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  public Service ensureFunctionalTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.FUNCTIONAL_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.WAR).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.ARTIFACTORY_ECHO_WAR);
    return owners.obtainService();
  }

  public Service ensureGenericArtifactoryTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.WAR).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.ARTIFACTORY_ECHO_WAR);
    return owners.obtainService();
  }

  public Service ensureMultiArtifactFunctionalTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.FUNCTIONAL_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.WAR).build()));
    return owners.obtainService();
  }

  public Service ensureAmiGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.AMI).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.AWS_AMI);
    return owners.obtainService();
  }

  public Service ensureAmazonS3GenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.IIS_APP).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_IIS_APP_S3);
    return owners.obtainService();
  }

  public Service ensureBambooGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.WAR).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.BAMBOO_METADATA_ONLY);
    return owners.obtainService();
  }

  public Service ensureJenkinsGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.WAR).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.JENKINS_METADATA_ONLY);
    return owners.obtainService();
  }

  public Service ensureNexusMavenGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.JAR).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.NEXUS3_MAVEN_METADATA_ONLY);
    return owners.obtainService();
  }

  public Service ensureNexusNpmGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.JAR).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.NEXUS3_NPM_METADATA_ONLY);
    return owners.obtainService();
  }

  public Service ensureNexusDockerGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.DOCKER).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.NEXUS3_DOCKER_METADATA_ONLY);
    return owners.obtainService();
  }

  public Service ensureSpotinstAmiGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.AMI).build()));
    artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.SPOTINST_AMI);
    return owners.obtainService();
  }

  private Service ensureKubernetesGenericTest(Randomizer.Seed seed, Owners owners) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    return ensureService(
        seed, owners, builder().name(KUBERNETES_GENERIC_TEST.name()).artifactType(ArtifactType.DOCKER).build());
  }

  public Service ensureAwsLambdaGenericTest(Randomizer.Seed seed, Owners owners, String name) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners, builder().name(name).artifactType(ArtifactType.AWS_LAMBDA).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_EXAMPLE_LAMBDA);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(Collections.singletonList(artifactStream.getUuid()));
    if (serviceResourceService.getLambdaSpecification(service.getAppId(), service.getUuid()) == null) {
      LambdaSpecification lambdaSpecification =
          LambdaSpecification.builder()
              .serviceId(service.getUuid())
              .defaults(DefaultSpecification.builder().runtime("nodejs12.x").memorySize(128).timeout(3).build())
              .functions(Collections.singletonList(FunctionSpecification.builder()
                                                       .functionName("functional-test-lambda")
                                                       .memorySize(128)
                                                       .runtime("nodejs12.x")
                                                       .handler("index.handler")
                                                       .timeout(3)
                                                       .build()))
              .build();
      lambdaSpecification.setAppId(owners.obtainApplication().getUuid());
      serviceResourceService.createLambdaSpecification(lambdaSpecification);
    }
    return service;
  }

  public Service ensureAzureVMSSService(Seed seed, Owners owners, String serviceName) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(
        seed, owners, builder().name(serviceName).artifactType(ArtifactType.AZURE_MACHINE_IMAGE).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.AZURE_MACHINE_IMAGE_LINUX_GALLERY);
    Service service = owners.obtainService();
    service.setArtifactStreams(Collections.singletonList(artifactStream));
    return service;
  }

  public Service ensureAzureWebAppService(Seed seed, Owners owners, String serviceName) {
    owners.obtainApplication(() -> applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST));
    owners.add(ensureService(seed, owners,
        builder().name(serviceName).deploymentType(AZURE_WEBAPP).artifactType(ArtifactType.DOCKER).build()));
    ArtifactStream artifactStream =
        artifactStreamManager.ensurePredefined(seed, owners, ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreams(Collections.singletonList(artifactStream));
    return service;
  }

  public Service ensureRandom(Randomizer.Seed seed, Owners owners) {
    EnhancedRandom random = Randomizer.instance(seed);
    Services predefined = random.nextObject(Services.class);
    return ensurePredefined(seed, owners, predefined);
  }

  public Service exists(Service service) {
    return wingsPersistence.createQuery(Service.class)
        .filter(ServiceKeys.appId, service.getAppId())
        .filter(ServiceKeys.name, service.getName())
        .get();
  }

  public Service ensureService(Randomizer.Seed seed, Owners owners, Service service) {
    EnhancedRandom random = Randomizer.instance(seed);

    ServiceBuilder builder = Service.builder();

    if (service != null && service.getAppId() != null) {
      builder.appId(service.getAppId());
    } else {
      Application application = owners.obtainApplication(() -> applicationGenerator.ensureRandom(seed, owners));
      builder.appId(application.getUuid());
    }

    if (service != null && service.getName() != null) {
      builder.name(service.getName());
    } else {
      builder.name(random.nextObject(String.class));
    }

    Service existing = exists(builder.build());
    if (existing != null) {
      return existing;
    }

    if (service != null && service.getDescription() != null) {
      builder.description(service.getDescription());
    } else {
      builder.description(random.nextObject(String.class));
    }

    if (service != null && service.getArtifactType() != null) {
      builder.artifactType(service.getArtifactType());
    } else {
      builder.artifactType(random.nextObject(ArtifactType.class));
    }

    if (service != null) {
      builder.deploymentType(service.getDeploymentType());
      builder.isK8sV2(service.isK8sV2());
    }

    if (service != null && service.getDeploymentTypeTemplateId() != null) {
      builder.deploymentTypeTemplateId(service.getDeploymentTypeTemplateId());
    }

    if (service != null && service.getCreatedBy() != null) {
      builder.createdBy(service.getCreatedBy());
    } else {
      builder.createdBy(owners.obtainUser());
    }

    if (service != null && service.getHelmVersion() != null) {
      builder.helmVersion(service.getHelmVersion());
    }

    final Service finalService = builder.build();

    return GeneratorUtils.suppressDuplicateException(
        () -> serviceResourceService.save(finalService), () -> exists(finalService));
  }
}
