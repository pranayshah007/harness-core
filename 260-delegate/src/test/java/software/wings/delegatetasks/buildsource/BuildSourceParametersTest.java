/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRASHANT;

import static software.wings.beans.StringValue.Builder.aStringValue;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.BambooConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceParametersBuilder;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BuildSourceParametersTest extends WingsBaseTest {
  private static final String DOCKER_URL = "https://registry.hub.docker.com/v2/";
  private static final String JENKINS_URL = "https://jenkins.wings.software";
  private static final String BAMBOO_URL = "http://ec2-34-205-16-35.compute-1.amazonaws.com:8085";
  private static final String ARTIFACTORY_URL = "https://harness.jfrog.io/harness";
  private static final String NEXUS_URL = "https://nexus2.harness.io";

  BuildSourceParametersBuilder sourceParametersBuilder =
      BuildSourceParameters.builder()
          .accountId(ACCOUNT_ID)
          .appId(APP_ID)
          .buildSourceRequestType(BuildSourceParameters.BuildSourceRequestType.GET_BUILDS)
          .limit(-1)
          .isCollection(true);

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesDocker() {
    BuildSourceParameters buildSourceParameters = sourceParametersBuilder
                                                      .settingValue(DockerConfig.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .dockerRegistryUrl(DOCKER_URL)
                                                                        .username("userName")
                                                                        .password("password".toCharArray())
                                                                        .build())
                                                      .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo(DOCKER_URL);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesJenkins() {
    BuildSourceParameters buildSourceParameters = sourceParametersBuilder
                                                      .settingValue(JenkinsConfig.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .jenkinsUrl(JENKINS_URL)
                                                                        .username("userName")
                                                                        .password("password".toCharArray())
                                                                        .build())
                                                      .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo(JENKINS_URL);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesBamboo() {
    BuildSourceParameters buildSourceParameters = sourceParametersBuilder
                                                      .settingValue(BambooConfig.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .bambooUrl(BAMBOO_URL)
                                                                        .username("userName")
                                                                        .password("password".toCharArray())
                                                                        .build())
                                                      .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo(BAMBOO_URL);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesArtifactory() {
    BuildSourceParameters buildSourceParameters = sourceParametersBuilder
                                                      .settingValue(ArtifactoryConfig.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .artifactoryUrl(ARTIFACTORY_URL)
                                                                        .username("userName")
                                                                        .password("password".toCharArray())
                                                                        .build())
                                                      .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo(ARTIFACTORY_URL);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesNexus() {
    BuildSourceParameters buildSourceParameters = sourceParametersBuilder
                                                      .settingValue(NexusConfig.builder()
                                                                        .accountId(ACCOUNT_ID)
                                                                        .nexusUrl(NEXUS_URL)
                                                                        .username("userName")
                                                                        .password("password".toCharArray())
                                                                        .build())
                                                      .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo(NEXUS_URL);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesGCR() {
    BuildSourceParameters buildSourceParameters =
        sourceParametersBuilder.settingValue(aStringValue().withValue("value").build())
            .artifactStreamType(ArtifactStreamType.GCR.name())
            .artifactStreamAttributes(ArtifactStreamAttributes.builder().registryHostName("gcr.io").build())
            .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo("https://gcr.io");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesGCRWithEnhancedExecCapabilities() {
    BuildSourceParameters buildSourceParameters =
        sourceParametersBuilder.settingValue(aStringValue().withValue("value").build())
            .artifactStreamType(ArtifactStreamType.GCR.name())
            .artifactStreamAttributes(ArtifactStreamAttributes.builder()
                                          .registryHostName("gcr.io")
                                          .enhancedGcrConnectivityCheckEnabled(true)
                                          .imageName("image")
                                          .build())
            .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo("https://gcr.io/v2/image/tags/list");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesACR() {
    BuildSourceParameters buildSourceParameters =
        sourceParametersBuilder.settingValue(aStringValue().withValue("value").build())
            .artifactStreamType(ArtifactStreamType.ACR.name())
            .artifactStreamAttributes(
                ArtifactStreamAttributes.builder().registryHostName("harnessprod.azurecr.io").build())
            .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo("https://harnessprod.azurecr.io");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesACRDefault() {
    BuildSourceParameters buildSourceParameters =
        sourceParametersBuilder.settingValue(aStringValue().withValue("value").build())
            .artifactStreamType(ArtifactStreamType.ACR.name())
            .artifactStreamAttributes(ArtifactStreamAttributes.builder().build())
            .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
    assertThat(capabilityList.get(0)).isExactlyInstanceOf(HttpConnectionExecutionCapability.class);
    HttpConnectionExecutionCapability connectionExecutionCapability =
        (HttpConnectionExecutionCapability) capabilityList.get(0);
    assertThat(connectionExecutionCapability.fetchCapabilityBasis()).isEqualTo("https://azure.microsoft.com");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesOthers() {
    BuildSourceParameters buildSourceParameters =
        sourceParametersBuilder.settingValue(aStringValue().withValue("value").build())
            .artifactStreamType(ArtifactStreamType.SMB.name())
            .artifactStreamAttributes(ArtifactStreamAttributes.builder().build())
            .build();
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(0);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFetchRequiredExecutionCapabilitiesOthersWithDelegateSelectors() {
    BuildSourceParameters buildSourceParameters =
        sourceParametersBuilder.settingValue(aStringValue().withValue("value").build())
            .artifactStreamType(ArtifactStreamType.SMB.name())
            .artifactStreamAttributes(ArtifactStreamAttributes.builder().build())
            .buildCollectParameters(
                BuildCollectParameters.builder().delegateSelectors(Collections.singleton("DELEGATE_SELECTOR")).build())
            .build();
    buildSourceParameters.setBuildSourceRequestType(BuildSourceParameters.BuildSourceRequestType.GET_BUILD);
    List<ExecutionCapability> capabilityList = buildSourceParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(capabilityList).hasSize(1);
  }
}
