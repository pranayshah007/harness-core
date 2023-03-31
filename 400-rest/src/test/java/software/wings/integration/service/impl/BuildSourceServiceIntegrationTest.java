/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.service.impl;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.utils.ArtifactType.DOCKER;
import static software.wings.utils.ArtifactType.RPM;
import static software.wings.utils.ArtifactType.WAR;
import static software.wings.utils.WingsTestConstants.HARNESS_ARTIFACTORY;
import static software.wings.utils.WingsTestConstants.HARNESS_BAMBOO;
import static software.wings.utils.WingsTestConstants.HARNESS_DOCKER_REGISTRY;
import static software.wings.utils.WingsTestConstants.HARNESS_NEXUS;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.Repeat;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.integration.IntegrationTestBase;
import software.wings.service.intfc.AmazonS3BuildService;
import software.wings.service.intfc.ArtifactoryBuildService;
import software.wings.service.intfc.BambooBuildService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.DockerBuildService;
import software.wings.service.intfc.EcrBuildService;
import software.wings.service.intfc.JenkinsBuildService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;

import com.amazonaws.regions.Regions;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

@RunWith(Parameterized.class)
public class BuildSourceServiceIntegrationTest extends IntegrationTestBase {
  @Parameter(0) public SettingVariableTypes type;
  @Parameter(1) public ArtifactStreamType streamType;
  @Parameter(2) public String repositoryType;
  @Parameter(3) public String jobName;
  @Parameter(4) public String groupId;
  @Parameter(5) public String artifactPath;
  @Parameter(6) public ArtifactType artifactType;

  private String accountId;
  private String appId;
  private SettingAttribute settingAttribute;
  private ArtifactStream artifactStream;
  private Application application;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Inject private BuildSourceService buildSourceService;
  @Inject private HPersistence persistence;
  @Inject private JenkinsBuildService jenkinsBuildService;
  @Inject private BambooBuildService bambooBuildService;
  @Inject private NexusBuildService nexusBuildService;
  @Inject private DockerBuildService dockerBuildService;
  @Inject private ArtifactoryBuildService artifactoryBuildService;
  @Inject private EcrBuildService ecrBuildService;
  @Inject private AmazonS3BuildService amazonS3BuildService;
  @Inject private ScmSecret scmSecret;
  @Inject private SecretManagementDelegateService secretManagementDelegateService;
  private final String userEmail = "rsingh@harness.io";
  private final String userName = "raghu";
  private final User user = User.Builder.anUser().email(userEmail).name(userName).build();

  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        {SettingVariableTypes.JENKINS, ArtifactStreamType.JENKINS, "", "todolist_war", "", "target/todolist.war", WAR},

        {SettingVariableTypes.BAMBOO, ArtifactStreamType.BAMBOO, "", "TOD-TOD", "", "artifacts/todolist.war", WAR},

        {SettingVariableTypes.NEXUS, ArtifactStreamType.NEXUS, "", "releases", "io.harness.test", "todolist", WAR},
        {SettingVariableTypes.DOCKER, ArtifactStreamType.DOCKER, "", "xyz", "", "wingsplugins/todolist", WAR},
        {SettingVariableTypes.ARTIFACTORY, ArtifactStreamType.ARTIFACTORY, "any", "generic-repo",
            "io/harness/todolist/todolist*", "", WAR},
        {SettingVariableTypes.ARTIFACTORY, ArtifactStreamType.ARTIFACTORY, "any", "harness-rpm", "todolist*", "", RPM},
        {SettingVariableTypes.ARTIFACTORY, ArtifactStreamType.ARTIFACTORY, "maven", "harness-maven",
            "io/harness/todolist/todolist/*/todolist*", "", WAR},
        {SettingVariableTypes.ARTIFACTORY, ArtifactStreamType.ARTIFACTORY, "docker", "docker", "wingsplugins/todolist",
            "", DOCKER},
        {SettingVariableTypes.ECR, ArtifactStreamType.ECR, "docker", Regions.US_EAST_1.getName(), "todolist",
            "todolist", DOCKER},
        {SettingVariableTypes.AMAZON_S3, ArtifactStreamType.AMAZON_S3, "", "harness-example", "",
            "harness-example/todolist.war", WAR},
    });
  }

  @Override
  @Before
  public void setUp() throws IllegalAccessException {
    initMocks(this);
    //    accountId = UUID.randomUUID().toString();
    //    appId = UUID.randomUUID().toString();
    //    wingsPersistence.save(user);
    //    UserThreadLocal.set(user);
    //    Service service = Service.builder().appId(appId).artifactType(artifactType).name("Some service").build();
    //    wingsPersistence.save(service);
    //    Owners owners = new Owners();
    //    owners.add(account);

    //    Seed seed = new Seed(0);
    //    Account account = accountGenerator.ensureGenericTest();
    //    seed = Randomizer.seed();
    //    Owners owners = ownerManager.create();
    //    application = owners.obtainApplication(()
    //                                               -> applicationGenerator.ensurePredefined(Randomizer.seed(),
    //                                                   ownerManager.create(), Applications.GENERIC_TEST));
    switch (type) {
      case JENKINS:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(jenkinsBuildService);
        //        owners = new Owners();
        //        owners.add(account);
        //        settingAttribute = settingGenerator.ensurePredefined(seed, owners, HARNESS_JENKINS_CONNECTOR);
        artifactStream = new JenkinsArtifactStream();
        ((JenkinsArtifactStream) artifactStream).setJobname(jobName);
        ((JenkinsArtifactStream) artifactStream).setArtifactPaths(Collections.singletonList(artifactPath));
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;
      case BAMBOO:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(bambooBuildService);
        settingAttribute =
            aSettingAttribute()
                .withName(HARNESS_BAMBOO)
                .withCategory(CONNECTOR)
                .withAccountId(accountId)
                .withValue(BambooConfig.builder()
                               .accountId(accountId)
                               .bambooUrl("http://ec2-34-205-16-35.compute-1.amazonaws.com:8085/")
                               .username("wingsbuild")
                               .password(scmSecret.decryptToCharArray(new SecretName("bamboo_connector_password")))
                               .build())
                .build();
        artifactStream = new BambooArtifactStream();
        ((BambooArtifactStream) artifactStream).setJobname(jobName);
        ((BambooArtifactStream) artifactStream).setArtifactPaths(Collections.singletonList(artifactPath));
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;

      case NEXUS:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(nexusBuildService);
        settingAttribute =
            aSettingAttribute()
                .withName(HARNESS_NEXUS)
                .withCategory(CONNECTOR)
                .withAccountId(accountId)
                .withValue(NexusConfig.builder()
                               .accountId(accountId)
                               .nexusUrl("https://nexus.wings.software")
                               .username("admin")
                               .password(scmSecret.decryptToCharArray(new SecretName("nexus_connector_password")))
                               .build())
                .build();
        artifactStream = new NexusArtifactStream();
        ((NexusArtifactStream) artifactStream).setJobname(jobName);
        ((NexusArtifactStream) artifactStream).setArtifactPaths(Collections.singletonList(artifactPath));
        ((NexusArtifactStream) artifactStream).setGroupId(groupId);
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;

      case DOCKER:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(dockerBuildService);
        settingAttribute =
            aSettingAttribute()
                .withName(HARNESS_DOCKER_REGISTRY)
                .withCategory(CONNECTOR)
                .withAccountId(accountId)
                .withValue(DockerConfig.builder()
                               .accountId(accountId)
                               .dockerRegistryUrl("https://registry.hub.docker.com/v2/")
                               .username("wingsplugins")
                               .password(scmSecret.decryptToCharArray(new SecretName("docker_connector_password")))
                               .build())
                .build();
        artifactStream = new DockerArtifactStream();
        ((DockerArtifactStream) artifactStream).setImageName(artifactPath);
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;
      case ARTIFACTORY:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(artifactoryBuildService);
        settingAttribute =
            aSettingAttribute()
                .withName(HARNESS_ARTIFACTORY)
                .withCategory(CONNECTOR)
                .withAccountId(accountId)
                .withValue(ArtifactoryConfig.builder()
                               .accountId(accountId)
                               .artifactoryUrl("https://harness.jfrog.io/harness")
                               .username("admin")
                               .password(scmSecret.decryptToCharArray(new SecretName("artifactory_connector_password")))
                               .build())
                .build();
        artifactStream = new ArtifactoryArtifactStream();
        ((ArtifactoryArtifactStream) artifactStream).setJobname(jobName);
        ((ArtifactoryArtifactStream) artifactStream).setArtifactPattern(groupId);
        ((ArtifactoryArtifactStream) artifactStream).setRepositoryType(repositoryType);
        ((ArtifactoryArtifactStream) artifactStream).setImageName(groupId);
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;
      case ECR:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(ecrBuildService);
        settingAttribute =
            aSettingAttribute()
                .withName("AWS")
                .withCategory(CLOUD_PROVIDER)
                .withAccountId(accountId)
                .withValue(AwsConfig.builder()
                               .accountId(accountId)
                               .accessKey(scmSecret.decryptToCharArray(new SecretName("ecr_connector_access_key")))
                               .secretKey(scmSecret.decryptToCharArray(new SecretName("ecr_connector_secret_key")))
                               .build())
                .build();
        artifactStream = new EcrArtifactStream();
        ((EcrArtifactStream) artifactStream).setRegion(Regions.US_EAST_1.getName());
        ((EcrArtifactStream) artifactStream).setImageName(groupId);
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;
      case AMAZON_S3:
        when(delegateProxyFactory.getV2(any(), any(SyncTaskContext.class))).thenReturn(amazonS3BuildService);
        settingAttribute =
            aSettingAttribute()
                .withName("AWS")
                .withCategory(CONNECTOR)
                .withAccountId(accountId)
                .withValue(AwsConfig.builder()
                               .accountId(accountId)
                               .accessKey(scmSecret.decryptToCharArray(new SecretName("ecr_connector_access_key")))
                               .secretKey(scmSecret.decryptToCharArray(new SecretName("ecr_connector_secret_key")))
                               .build())
                .build();
        artifactStream = new AmazonS3ArtifactStream();
        ((AmazonS3ArtifactStream) artifactStream).setArtifactPaths(asList("todolist.war"));
        ((AmazonS3ArtifactStream) artifactStream).setJobname("harness-example");
        artifactStream.setServiceId(SERVICE_ID);
        artifactStream.setAppId(appId);
        break;
      default:
        throw new IllegalArgumentException("Invalid type: " + type);
    }
    //    wingsPersistence.save(artifactStream);
    //    wingsPersistence.save(settingAttribute);
    FieldUtils.writeField(buildSourceService, "delegateProxyFactory", delegateProxyFactory, true);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getJobs() {
    switch (type) {
      case JENKINS:
        Set<JobDetails> jobs = buildSourceService.getJobs(application.getUuid(), settingAttribute.getUuid(), null);
        assertThat(jobs.isEmpty()).isFalse();
        return;
      case ARTIFACTORY:
      case ECR:
        return;
      default:
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getPlans() {
    switch (type) {
      case AMAZON_S3:
        Map<String, String> plans =
            buildSourceService.getPlans(application.getUuid(), settingAttribute.getUuid(), streamType.name());
        assertThat(plans.isEmpty()).isFalse();
        return;
      default:
        return;
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getPlansWithService() {
    switch (type) {
      case DOCKER:
      case ECR:
        return;
      default:
        Service service = Service.builder().appId(appId).artifactType(WAR).name("Some service").build();
        persistence.save(service);
        Map<String, String> plans = buildSourceService.getPlans(
            appId, settingAttribute.getUuid(), service.getUuid(), streamType.name(), repositoryType);
        assertThat(plans.isEmpty()).isFalse();
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getArtifactPaths() {
    switch (type) {
      case DOCKER:
      case ARTIFACTORY:
        return;
      default:
        Set<String> artifactPaths =
            buildSourceService.getArtifactPaths(appId, jobName, settingAttribute.getUuid(), groupId, streamType.name());
        assertThat(artifactPaths.isEmpty()).isFalse();
        assertThat(artifactPaths.contains(artifactPath)).isTrue();
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getBuilds() {
    switch (type) {
      case DOCKER:
      case ECR:
        return;
      default:
        List<BuildDetails> builds =
            buildSourceService.getBuilds(appId, artifactStream.getUuid(), settingAttribute.getUuid());
        assertThat(builds.isEmpty()).isFalse();
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getLastSuccessfulBuild() {
    switch (type) {
      case DOCKER:
      case ECR:
        return;
      case ARTIFACTORY:
        switch (repositoryType) {
          case "any":
          case "docker":
            return;
          case "maven":
            Service service = Service.builder().appId(appId).artifactType(WAR).name("Some service").build();
            persistence.save(service);
            BuildDetails build =
                buildSourceService.getLastSuccessfulBuild(appId, artifactStream.getUuid(), settingAttribute.getUuid());
            assertThat(build).isNotNull();
            break;
          default:
            throw new IllegalArgumentException("invalid repo type");
        }
        break;

      default:
        Service service = Service.builder().appId(appId).artifactType(WAR).name("Some service").build();
        persistence.save(service);
        BuildDetails build =
            buildSourceService.getLastSuccessfulBuild(appId, artifactStream.getUuid(), settingAttribute.getUuid());
        assertThat(build).isNotNull();
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void getGroupIds() {
    Set<String> groupIds = null;
    switch (type) {
      case JENKINS:
      case BAMBOO:
      case DOCKER:
      case ECR:
        assertThatExceptionOfType(WingsException.class)
            .isThrownBy(() -> buildSourceService.getGroupIds(appId, jobName, settingAttribute.getUuid()));
        break;
      case ARTIFACTORY:
        switch (repositoryType) {
          case "any":
            return;
          case "maven":
          case "docker":
            groupIds = buildSourceService.getGroupIds(appId, jobName, settingAttribute.getUuid());
            break;
          default:
            throw new IllegalArgumentException("invalid repo type: " + repositoryType);
        }
        break;
      case NEXUS:
        groupIds = buildSourceService.getGroupIds(appId, jobName, settingAttribute.getUuid());
        break;
      default:
        throw new IllegalArgumentException("invalid type: " + type);
    }
    assertThat(groupIds).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void validateArtifactSource() {
    switch (type) {
      case JENKINS:
      case BAMBOO:
      case NEXUS:
        assertThat(buildSourceService.validateArtifactSource(appId, settingAttribute.getUuid(), null)).isTrue();
        break;
      case ARTIFACTORY:
        if (repositoryType.equals("docker")) {
          return;
        }
        break;
      case DOCKER:
        assertThat(buildSourceService.validateArtifactSource(
                       appId, settingAttribute.getUuid(), artifactStream.fetchArtifactStreamAttributes(null)))
            .isTrue();
        break;
      case ECR:
        return;
      default:
        throw new IllegalArgumentException("invalid type: " + type);
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void getLabels() {
    switch (type) {
      case JENKINS:
      case BAMBOO:
      case NEXUS:
      case ARTIFACTORY:
      case ECR:
      case AMAZON_S3:
        List<Map<String, String>> labels = buildSourceService.getLabels(artifactStream, asList("1"));
        assertThat(labels).isEmpty();
        break;
      case DOCKER:
        break;
      default:
        throw new IllegalArgumentException("invalid type: " + type);
    }
  }
}
