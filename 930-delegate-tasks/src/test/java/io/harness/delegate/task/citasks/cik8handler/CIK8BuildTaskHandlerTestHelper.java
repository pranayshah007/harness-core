/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.FILE;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;
import static io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder.SECRET_KEY;

import static org.mockito.Mockito.mock;

import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.k8s.model.ImageDetails;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CIK8BuildTaskHandlerTestHelper {
  public static final String containerName2 = "container2";
  private static final String namespace = "default";
  private static final String podName = "pod";
  private static final String podName1 = "pod1";
  private static final String imageName = "IMAGE";
  private static final String containerName1 = "container1";
  private static final String tag = "TAG";
  private static final String registryUrl = "https://index.docker.io/v1/";
  private static final String svcName = "service";
  public static final String commitId = "050ec93c9767b8759a07b7a99312974b7acb5d54";
  public static final String branch_name = "master";
  private static final String gitSshRepoUrl = "git@github.com:wings-software/portal.git";
  private static final String gitSshKey = "git_ssh_key";

  private static String storageClass = "test-storage";
  private static Integer storageMib = 100;
  private static String claimName = "pvc";
  private static String volume1 = "volume1";
  private static final int timeout = 100;

  public static CIK8InitializeTaskParams buildGitSecretErrorTaskParams() {
    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .name(podName)
                                                           .namespace(namespace)
                                                           .gitConnector(ConnectorDetails.builder().build())
                                                           .containerParamsList(containerParamsList)
                                                           .build();
    return CIK8InitializeTaskParams.builder().cik8PodParams(cik8PodParams).podMaxWaitUntilReadySecs(timeout).build();
  }

  public static CIK8InitializeTaskParams buildImageSecretErrorTaskParams() {
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetails imageDetailsWithoutRegistry = ImageDetails.builder().name(imageName).tag(tag).build();

    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetails).build())
            .build());
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .imageDetailsWithConnector(
                ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutRegistry).build())
            .build());

    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .name(podName)
                                                           .namespace(namespace)
                                                           .gitConnector(ConnectorDetails.builder().build())
                                                           .containerParamsList(containerParamsList)
                                                           .build();

    return CIK8InitializeTaskParams.builder().cik8PodParams(cik8PodParams).podMaxWaitUntilReadySecs(timeout).build();
  }

  public static CIK8InitializeTaskParams buildPodCreateErrorTaskParams() {
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetails imageDetailsWithoutRegistry = ImageDetails.builder().name(imageName).tag(tag).build();

    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetails).build())
            .build());
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .imageDetailsWithConnector(
                ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutRegistry).build())
            .build());

    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .name(podName)
                                                           .namespace(namespace)
                                                           .gitConnector(ConnectorDetails.builder().build())
                                                           .containerParamsList(containerParamsList)
                                                           .build();

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(ConnectorDetails.builder().build())
        .cik8PodParams(cik8PodParams)
        .podMaxWaitUntilReadySecs(timeout)
        .build();
  }

  public static CIK8InitializeTaskParams buildTaskParams() {
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetails imageDetailsWithoutRegistry = ImageDetails.builder().name(imageName).tag(tag).build();

    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .name(containerName1)
            .containerType(CIContainerType.ADD_ON)
            .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                           .imageConnectorDetails(getDockerConnectorDetails())
                                           .imageDetails(imageDetails)
                                           .build())
            .containerSecrets(
                ContainerSecrets.builder().connectorDetailsMap(getPublishArtifactConnectorDetails()).build())
            .build());
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .containerSecrets(ContainerSecrets.builder().secretVariableDetails(getSecretVariableDetails()).build())
            .name(containerName2)
            .containerType(CIContainerType.STEP_EXECUTOR)
            .imageDetailsWithConnector(
                ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutRegistry).build())
            .build());

    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .name(podName)
                                                           .namespace(namespace)
                                                           .gitConnector(getGitConnector())
                                                           .containerParamsList(containerParamsList)
                                                           .build();

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(getK8sConnector())
        .cik8PodParams(cik8PodParams)
        .podMaxWaitUntilReadySecs(timeout)
        .build();
  }

  private static ConnectorDetails getGitConnector() {
    return ConnectorDetails.builder()
        .connectorType(ConnectorType.GIT)
        .connectorConfig(GitConfigDTO.builder()
                             .gitAuthType(GitAuthType.SSH)
                             .gitAuth(GitSSHAuthenticationDTO.builder()
                                          .encryptedSshKey(SecretRefHelper.createSecretRef(gitSshKey))
                                          .build())
                             .url(gitSshRepoUrl)
                             .build())
        .build();
  }

  private static ConnectorDetails getK8sConnector() {
    return ConnectorDetails.builder()
        .connectorConfig(KubernetesClusterConfigDTO.builder().build())
        .encryptedDataDetails(Collections.singletonList(EncryptedDataDetail.builder().build()))
        .build();
  }

  public static CIK8InitializeTaskParams buildTaskParamsWithPodSvc() {
    List<EncryptedDataDetail> encryptionDetails = mock(List.class);
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetails imageDetailsWithoutRegistry = ImageDetails.builder().name(imageName).tag(tag).build();

    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .name(containerName1)
            .containerType(CIContainerType.ADD_ON)
            .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetails).build())
            .containerSecrets(
                ContainerSecrets.builder().connectorDetailsMap(getPublishArtifactConnectorDetails()).build())
            .build());
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .containerSecrets(ContainerSecrets.builder().secretVariableDetails(getSecretVariableDetails()).build())
            .name(containerName2)
            .containerType(CIContainerType.STEP_EXECUTOR)
            .imageDetailsWithConnector(
                ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutRegistry).build())
            .build());

    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .gitConnector(getGitConnector())
                                                           .branchName(branch_name)
                                                           .commitId(commitId)
                                                           .name(podName)
                                                           .namespace(namespace)
                                                           .containerParamsList(containerParamsList)
                                                           .build();

    CIK8PodParams<CIK8ContainerParams> cik8PodParams1 = CIK8PodParams.<CIK8ContainerParams>builder()
                                                            .name(podName1)
                                                            .namespace(namespace)
                                                            .containerParamsList(containerParamsList)
                                                            .build();

    List<Integer> ports = new ArrayList<>();
    ports.add(8000);
    Map<String, String> selector = new HashMap<>();
    CIK8ServicePodParams cik8ServicePodParams = CIK8ServicePodParams.builder()
                                                    .serviceName(svcName)
                                                    .ports(ports)
                                                    .selectorMap(selector)
                                                    .cik8PodParams(cik8PodParams1)
                                                    .build();

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(getK8sConnector())
        .cik8PodParams(cik8PodParams)
        .servicePodParams(Arrays.asList(cik8ServicePodParams))
        .podMaxWaitUntilReadySecs(timeout)
        .build();
  }

  public static CIK8InitializeTaskParams buildTaskParamsWithPVC() {
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetails imageDetailsWithoutRegistry = ImageDetails.builder().name(imageName).tag(tag).build();

    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .name(containerName1)
            .containerType(CIContainerType.ADD_ON)
            .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetails).build())
            .containerSecrets(
                ContainerSecrets.builder().connectorDetailsMap(getPublishArtifactConnectorDetails()).build())
            .build());
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .containerSecrets(ContainerSecrets.builder().secretVariableDetails(getSecretVariableDetails()).build())
            .name(containerName2)
            .containerType(CIContainerType.STEP_EXECUTOR)
            .imageDetailsWithConnector(
                ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutRegistry).build())
            .build());

    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .name(podName)
                                                           .namespace(namespace)
                                                           .gitConnector(ConnectorDetails.builder().build())
                                                           .containerParamsList(containerParamsList)
                                                           .pvcParamList(Arrays.asList(PVCParams.builder()
                                                                                           .volumeName(volume1)
                                                                                           .claimName(claimName)
                                                                                           .storageClass(storageClass)
                                                                                           .isPresent(false)
                                                                                           .sizeMib(storageMib)
                                                                                           .build()))
                                                           .build();

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(ConnectorDetails.builder().build())
        .cik8PodParams(cik8PodParams)
        .podMaxWaitUntilReadySecs(timeout)
        .build();
  }

  public static Map<String, ConnectorDetails> getPublishArtifactConnectorDetails() {
    return Collections.singletonMap("docker", getDockerConnectorDetails());
  }

  public static ConnectorDetails getDockerConnectorDetails() {
    return ConnectorDetails.builder()
        .encryptedDataDetails(Collections.singletonList(
            EncryptedDataDetail.builder()
                .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
                .build()))

        .connectorType(ConnectorType.DOCKER)
        .connectorConfig(
            DockerConnectorDTO.builder()
                .dockerRegistryUrl("https://index.docker.io/v1/")
                .auth(DockerAuthenticationDTO.builder()
                          .authType(DockerAuthType.USER_PASSWORD)
                          .credentials(
                              DockerUserNamePasswordDTO.builder()
                                  .username("uName")
                                  .passwordRef(SecretRefData.builder().decryptedValue("pWord".toCharArray()).build())
                                  .build())
                          .build())
                .build())
        .build();
  }

  public static List<SecretVariableDetails> getSecretVariableDetails() {
    List<SecretVariableDetails> secretVariableDetailsList = new ArrayList<>();

    SecretVariableDetails secretVariableDetails =
        SecretVariableDetails.builder()
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .name("abc")
                                   .secret(SecretRefData.builder().scope(Scope.ACCOUNT).identifier("secretId").build())
                                   .build())
            .encryptedDataDetailList(Collections.singletonList(
                EncryptedDataDetail.builder()
                    .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
                    .build()))
            .build();

    secretVariableDetailsList.add(secretVariableDetails);
    return secretVariableDetailsList;
  }

  public static Map<String, SecretParams> getCustomVarSecret() {
    Map<String, SecretParams> decryptedSecrets = new HashMap<>();
    decryptedSecrets.put("docker",
        SecretParams.builder().type(TEXT).secretKey(SECRET_KEY + "docker").value(encodeBase64("pass")).build());
    return decryptedSecrets;
  }

  public static Map<String, SecretParams> getGcpSecret() {
    Map<String, SecretParams> decryptedSecrets = new HashMap<>();
    decryptedSecrets.put("SECRET_PATH_gcp",
        SecretParams.builder().type(FILE).secretKey("SECRET_PATH_gcp").value(encodeBase64("configFile:{}")).build());
    return decryptedSecrets;
  }
  public static Map<String, SecretParams> getDockerSecret() {
    Map<String, SecretParams> decryptedSecrets = new HashMap<>();
    decryptedSecrets.put("USERNAME_docker",
        SecretParams.builder().type(TEXT).secretKey("USERNAME_docker").value(encodeBase64("uname")).build());
    decryptedSecrets.put("PASSWORD_docker",
        SecretParams.builder().type(TEXT).secretKey("PASSWORD_docker").value(encodeBase64("passw")).build());
    decryptedSecrets.put("ENDPOINT_docker",
        SecretParams.builder().type(TEXT).secretKey("ENDPOINT_docker").value(encodeBase64("endpoint")).build());
    return decryptedSecrets;
  }
  public static Map<String, SecretParams> getPublishArtifactSecrets() {
    Map<String, SecretParams> decryptedSecrets = new HashMap<>();
    decryptedSecrets.putAll(getDockerSecret());
    decryptedSecrets.putAll(getGcpSecret());
    return decryptedSecrets;
  }

  public static CIK8InitializeTaskParams buildTaskParamsWithWhitespace() {
    ImageDetails imageDetails = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetails imageDetailsWithoutRegistry = ImageDetails.builder().name(imageName).tag(tag).build();

    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .name(containerName1)
            .containerType(CIContainerType.ADD_ON)
            .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                           .imageConnectorDetails(getDockerConnectorDetails())
                                           .imageDetails(imageDetails)
                                           .build())
            .containerSecrets(
                ContainerSecrets.builder().connectorDetailsMap(getPublishArtifactConnectorDetails()).build())
            .build());
    containerParamsList.add(
        CIK8ContainerParams.builder()
            .containerSecrets(ContainerSecrets.builder().secretVariableDetails(getSecretVariableDetails()).build())
            .name(containerName2)
            .containerType(CIContainerType.STEP_EXECUTOR)
            .imageDetailsWithConnector(
                ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutRegistry).build())
            .build());

    CIK8PodParams<CIK8ContainerParams> cik8PodParams = CIK8PodParams.<CIK8ContainerParams>builder()
                                                           .name(podName)
                                                           .namespace(namespace + " ")
                                                           .gitConnector(getGitConnector())
                                                           .containerParamsList(containerParamsList)
                                                           .build();

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(getK8sConnector())
        .cik8PodParams(cik8PodParams)
        .podMaxWaitUntilReadySecs(timeout)
        .build();
  }
}
