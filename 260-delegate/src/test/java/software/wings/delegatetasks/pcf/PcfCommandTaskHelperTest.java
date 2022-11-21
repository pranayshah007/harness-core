/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.pcf;

import static io.harness.pcf.model.PcfConstants.IMAGE_MANIFEST_YML_ELEMENT;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.RIHAZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.delegatetasks.pcf.PcfTestConstants.ACCOUNT_ID;
import static software.wings.delegatetasks.pcf.PcfTestConstants.ACTIVITY_ID;
import static software.wings.delegatetasks.pcf.PcfTestConstants.APP_ID;
import static software.wings.delegatetasks.pcf.PcfTestConstants.ARIIFACTORY_URL;
import static software.wings.delegatetasks.pcf.PcfTestConstants.DOCKER_URL;
import static software.wings.delegatetasks.pcf.PcfTestConstants.ECR_URL;
import static software.wings.delegatetasks.pcf.PcfTestConstants.GCR_URL;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_ARTIFACTORY_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_DOCKER;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_DOCKER_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_ECR_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_EXTENDED_SUPPORT_REMOTE;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_EXTENDED_SUPPORT_REMOTE_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_GCR_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_LOCAL_EXTENDED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_LOCAL_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_LOCAL_WITH_TEMP_ROUTES_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_NO_ROUTE;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_NO_ROUTE_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_RANDOM_ROUTE;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_RANDOM_ROUTE_WITH_HOST;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_RANDON_ROUTE_RESOLVED;
import static software.wings.delegatetasks.pcf.PcfTestConstants.MANIFEST_YAML_RESOLVED_WITH_RANDOM_ROUTE;
import static software.wings.delegatetasks.pcf.PcfTestConstants.REGISTRY_HOST_NAME;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCliDelegateResolver;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AwsConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FilenameUtils;
import org.apache.cxf.helpers.FileUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public class PcfCommandTaskHelperTest extends WingsBaseTest {
  @Mock CfDeploymentManager pcfDeploymentManager;
  @Mock EncryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock LogCallback executionLogCallback;
  @Mock DelegateFileManager delegateFileManager;
  @Mock CfCliDelegateResolver cfCliDelegateResolver;

  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @InjectMocks @Spy PcfCommandTaskHelper pcfCommandTaskHelper;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateManifestYamlForPush() throws Exception {
    List<String> routes = Arrays.asList("app.harness.io", "stage.harness.io");
    List<String> tempRoutes = Arrays.asList("appTemp.harness.io", "stageTemp.harness.io");

    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder().routeMaps(routes).manifestYaml(MANIFEST_YAML).build();
    cfCommandSetupRequest.setArtifactStreamAttributes(ArtifactStreamAttributes.builder().build());
    CfCreateApplicationRequestData requestData = generatePcfCreateApplicationRequestData(cfCommandSetupRequest);

    // 1. Replace ${ROUTE_MAP with routes from setupRequest}
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML);
    cfCommandSetupRequest.setRouteMaps(routes);
    String finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_LOCAL_RESOLVED);

    // 2. Replace ${ROUTE_MAP with routes from setupRequest}
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML);
    cfCommandSetupRequest.setRouteMaps(tempRoutes);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_LOCAL_WITH_TEMP_ROUTES_RESOLVED);

    // 3. Simulation of BG, manifest contains final routes, but they should be replaced with tempRoutes,
    // which are mentioned in PcfSetupRequest
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML_LOCAL_EXTENDED);
    cfCommandSetupRequest.setRouteMaps(tempRoutes);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_LOCAL_WITH_TEMP_ROUTES_RESOLVED);

    // 4. Manifest contains no-route = true, ignore routes in setupRequest
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML_NO_ROUTE);
    cfCommandSetupRequest.setRouteMaps(tempRoutes);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_NO_ROUTE_RESOLVED);

    // 5. use random-route when no-routes are provided.
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML);
    cfCommandSetupRequest.setRouteMaps(null);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_RESOLVED_WITH_RANDOM_ROUTE);

    // 6. use random-route when no-routes are provided.
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML);
    cfCommandSetupRequest.setRouteMaps(emptyList());
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_RESOLVED_WITH_RANDOM_ROUTE);

    // 7. use random-route when no-routes are provided.
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML_RANDOM_ROUTE);
    cfCommandSetupRequest.setRouteMaps(null);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_RANDON_ROUTE_RESOLVED);

    // 8. use random-route when no-routes are provided.
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML_RANDOM_ROUTE_WITH_HOST);
    cfCommandSetupRequest.setRouteMaps(null);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_RANDON_ROUTE_RESOLVED);

    // 9
    cfCommandSetupRequest.setManifestYaml(MANIFEST_YAML_EXTENDED_SUPPORT_REMOTE);
    cfCommandSetupRequest.setRouteMaps(routes);
    finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_EXTENDED_SUPPORT_REMOTE_RESOLVED);
  }

  private CfCreateApplicationRequestData generatePcfCreateApplicationRequestData(
      CfCommandSetupRequest cfCommandSetupRequest) {
    return CfCreateApplicationRequestData.builder()
        .password("ABCD".toCharArray())
        .newReleaseName("app1__1")
        .artifactPath("/root/app")
        .cfRequestConfig(CfRequestConfig.builder().spaceName("space").build())
        .build();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateManifestYamlForDockerHubPush() throws Exception {
    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder().manifestYaml(MANIFEST_YAML_DOCKER).build();
    DockerConfig dockerConfig = DockerConfig.builder()
                                    .dockerRegistryUrl(DOCKER_URL)
                                    .username("admin")
                                    .password(new ScmSecret().decryptToCharArray(new SecretName("harness_docker_v2")))
                                    .accountId(Account.GLOBAL_ACCOUNT_ID)
                                    .build();
    populateDockerInfo(cfCommandSetupRequest, DOCKER_URL, dockerConfig);
    CfCreateApplicationRequestData requestData = generatePcfCreateApplicationRequestDataDocker(cfCommandSetupRequest);
    String finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_DOCKER_RESOLVED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateManifestYamlForECRPush() throws Exception {
    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder().manifestYaml(MANIFEST_YAML_DOCKER).build();
    AwsConfig awsConfig = AwsConfig.builder()
                              .accessKey("AKIAWQ5IKSASRV2RUSNP".toCharArray())
                              .secretKey("secretKey".toCharArray())
                              .build();
    populateDockerInfo(cfCommandSetupRequest, ECR_URL, awsConfig);
    CfCreateApplicationRequestData requestData = generatePcfCreateApplicationRequestDataDocker(cfCommandSetupRequest);
    String finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_ECR_RESOLVED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateManifestYamlForArtifactoryPush() throws Exception {
    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder().manifestYaml(MANIFEST_YAML_DOCKER).build();
    ArtifactoryConfig config = ArtifactoryConfig.builder().username("admin").password("key".toCharArray()).build();
    populateDockerInfo(cfCommandSetupRequest, ARIIFACTORY_URL, config);
    CfCreateApplicationRequestData requestData = generatePcfCreateApplicationRequestDataDocker(cfCommandSetupRequest);
    String finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_ARTIFACTORY_RESOLVED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGenerateManifestYamlForGCRPush() throws Exception {
    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder().manifestYaml(MANIFEST_YAML_DOCKER).build();
    GcpConfig gcpConfig = GcpConfig.builder().serviceAccountKeyFileContent("privateKey".toCharArray()).build();
    populateDockerInfo(cfCommandSetupRequest, GCR_URL, gcpConfig);
    CfCreateApplicationRequestData requestData = generatePcfCreateApplicationRequestDataDocker(cfCommandSetupRequest);
    String finalManifest = pcfCommandTaskHelper.generateManifestYamlForPush(cfCommandSetupRequest, requestData);
    assertThat(finalManifest).isEqualTo(MANIFEST_YAML_GCR_RESOLVED);
  }

  private void populateDockerInfo(CfCommandSetupRequest cfCommandSetupRequest, String url, SettingValue value) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(IMAGE_MANIFEST_YML_ELEMENT, url);

    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().build();
    artifactStreamAttributes.setDockerBasedDeployment(true);
    artifactStreamAttributes.setMetadata(metadata);

    SettingAttribute serverSetting = SettingAttribute.builder().value(value).build();
    artifactStreamAttributes.setServerSetting(serverSetting);
    cfCommandSetupRequest.setArtifactStreamAttributes(artifactStreamAttributes);
  }

  private CfCreateApplicationRequestData generatePcfCreateApplicationRequestDataDocker(
      CfCommandSetupRequest cfCommandSetupRequest) {
    return CfCreateApplicationRequestData.builder()
        .password("ABCD".toCharArray())
        .newReleaseName("app1__1")
        .cfRequestConfig(CfRequestConfig.builder().spaceName("space").build())
        .build();
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void testDownloadArtifact() throws IOException, ExecutionException {
    ArtifactStreamAttributes artifactStreamAttributes =
        ArtifactStreamAttributes.builder().artifactName("test-artifact").registryHostName(REGISTRY_HOST_NAME).build();
    CfCommandSetupRequest cfCommandSetupRequest = CfCommandSetupRequest.builder()
                                                      .artifactStreamAttributes(artifactStreamAttributes)
                                                      .accountId(ACCOUNT_ID)
                                                      .appId(APP_ID)
                                                      .activityId(ACTIVITY_ID)
                                                      .commandName(CfCommandRequest.PcfCommandType.SETUP.name())
                                                      .build();

    String randomToken = Long.toString(System.currentTimeMillis());

    String testFileName = randomToken + cfCommandSetupRequest.getArtifactStreamAttributes().getArtifactName();

    File workingDirectory = FileUtils.createTmpDir();
    File testArtifactFile = FileUtils.createTempFile(FilenameUtils.getName(testFileName), randomToken);

    when(delegateFileManager.downloadArtifactAtRuntime(cfCommandSetupRequest.getArtifactStreamAttributes(),
             cfCommandSetupRequest.getAccountId(), cfCommandSetupRequest.getAppId(),
             cfCommandSetupRequest.getActivityId(), cfCommandSetupRequest.getCommandName(),
             cfCommandSetupRequest.getArtifactStreamAttributes().getRegistryHostName()))
        .thenReturn(new FileInputStream(testArtifactFile));

    File artifactFile =
        pcfCommandTaskHelper.downloadArtifact(cfCommandSetupRequest, workingDirectory, executionLogCallback);

    assertThat(artifactFile.exists()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDownloadArtifactWithExtractScript() throws IOException, ExecutionException {
    String processedArtifactFilename = "test-artifact-processed";
    ArtifactStreamAttributes artifactStreamAttributes =
        ArtifactStreamAttributes.builder().artifactName("test-artifact").registryHostName(REGISTRY_HOST_NAME).build();
    CfCommandSetupRequest cfCommandSetupRequest =
        CfCommandSetupRequest.builder()
            .artifactStreamAttributes(artifactStreamAttributes)
            .timeoutIntervalInMin(1)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .commandName(CfCommandRequest.PcfCommandType.SETUP.name())
            .artifactProcessingScript("mv ${downloadedArtifact} " + processedArtifactFilename + "\ncp "
                + processedArtifactFilename + " ${processedArtifactDir}")
            .build();

    String randomToken = Long.toString(System.currentTimeMillis());

    String testFileName = randomToken + cfCommandSetupRequest.getArtifactStreamAttributes().getArtifactName();

    File workingDirectory = FileUtils.createTmpDir();
    File testArtifactFile = FileUtils.createTempFile(FilenameUtils.getName(testFileName), randomToken);

    when(delegateFileManager.downloadArtifactAtRuntime(cfCommandSetupRequest.getArtifactStreamAttributes(),
             cfCommandSetupRequest.getAccountId(), cfCommandSetupRequest.getAppId(),
             cfCommandSetupRequest.getActivityId(), cfCommandSetupRequest.getCommandName(),
             cfCommandSetupRequest.getArtifactStreamAttributes().getRegistryHostName()))
        .thenReturn(new FileInputStream(testArtifactFile));

    File artifactFile =
        pcfCommandTaskHelper.downloadArtifact(cfCommandSetupRequest, workingDirectory, executionLogCallback);

    assertThat(artifactFile.exists()).isTrue();
    assertThat(artifactFile.getName().contains(processedArtifactFilename)).isTrue();
  }
}
