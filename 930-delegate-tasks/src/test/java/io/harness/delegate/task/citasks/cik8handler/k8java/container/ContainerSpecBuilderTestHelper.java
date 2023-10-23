/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.k8java.container;

import static java.lang.String.format;

import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretVarParams;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.pod.SecretVolumeParams;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.model.ImageDetails;
import io.harness.security.encryption.EncryptedDataDetail;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1ContainerPortBuilder;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1EnvVarSourceBuilder;
import io.kubernetes.client.openapi.models.V1KeyToPathBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1ResourceRequirementsBuilder;
import io.kubernetes.client.openapi.models.V1SecretKeySelectorBuilder;
import io.kubernetes.client.openapi.models.V1SecretVolumeSourceBuilder;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1SecurityContextBuilder;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1VolumeMountBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerSpecBuilderTestHelper {
  private static String containerName = "container";
  private static List<String> commands = Arrays.asList("/bin/sh", "-c");
  private static List<String> args = Arrays.asList("echo hello_world");
  private static String workingDir = "/step/workspace";
  private static Integer port = 8001;

  private static String var1 = "hello";
  private static String value1 = "world";
  private static String var2 = "foo";
  private static String value2 = "bar";

  private static String secretVar = "connector_username";
  private static String secretName = "ConnectorDisconnectHandler";
  private static String secretKey = "username";

  private static String volume1 = "volume1";
  private static String mountPath1 = "/mnt1";
  private static String volume2 = "volume2";
  private static String mountPath2 = "/mnt2";

  private static String imageName = "IMAGE";
  private static String tag = "TAG";
  private static String imageCtrName = imageName + ":" + tag;
  private static String registryUrl = "https://index.docker.io/v1/";
  private static String userName = "usr";
  private static String password = "pwd";
  private static String imageSecret = "img-secret";
  private static String registrySecretName = "hs-index-docker-io-v1-usr-hs";

  private static Integer requestMemoryMib = 512;
  private static Integer limitMemoryMib = 1024;
  private static Integer requestMilliCpu = 100;
  private static Integer limitMilliCpu = 200;

  private static String secretVolumeMountPath = "/etc/secret";
  private static String secretsVolumeName = "secrets";
  private static V1SecurityContext securityContext = new V1SecurityContextBuilder().withPrivileged(false).build();

  public static CIK8ContainerParams basicCreateSpecInput() {
    ImageDetails imageDetailsWithoutCred =
        ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithoutCred).build())
        .commands(commands)
        .args(args)
        .build();
  }

  public static ContainerSpecBuilderResponse basicCreateSpecResponse() {
    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withSecurityContext(securityContext)
                                     .withPorts(new ArrayList<>())
                                     .withVolumeMounts(new ArrayList<>())
                                     .withEnv(new ArrayList<>());
    return ContainerSpecBuilderResponse.builder().containerBuilder(builder).volumes(new ArrayList<>()).build();
  }

  public static CIK8ContainerParams basicCreateSpecWithEnvInput() {
    ImageDetails imageWithoutCred = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    Map<String, String> envVars = new HashMap<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);

    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageWithoutCred).build())
        .commands(commands)
        .args(args)
        .envVars(envVars)
        .build();
  }

  public static ContainerSpecBuilderResponse basicCreateSpecWithEnvResponse() {
    Map<String, String> envVars = new HashMap<>();
    List<V1EnvVar> ctrEnvVars = new ArrayList<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    envVars.forEach((name, val) -> ctrEnvVars.add(new V1EnvVarBuilder().withName(name).withValue(val).build()));

    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withEnv(ctrEnvVars)
                                     .withSecurityContext(securityContext)
                                     .withPorts(new ArrayList<>())
                                     .withVolumeMounts(new ArrayList<>());
    return ContainerSpecBuilderResponse.builder().containerBuilder(builder).volumes(new ArrayList<>()).build();
  }

  public static Map<String, SecretVarParams> createSecretKeyParams() {
    Map<String, SecretVarParams> secretEnvVars = new HashMap<>();
    secretEnvVars.put(secretVar, SecretVarParams.builder().secretKey(secretKey).secretName(secretName).build());
    return secretEnvVars;
  }

  public static CIK8ContainerParams basicCreateSpecWithSecretEnvPortWorkingDir() {
    ImageDetails imageWithoutCred = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    Map<String, String> envVars = new HashMap<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);

    List<SecretVariableDetails> secretVariableDetailsList = new ArrayList<>();
    secretVariableDetailsList.add(
        SecretVariableDetails.builder()
            .encryptedDataDetailList(
                Collections.singletonList(EncryptedDataDetail.builder().fieldName("secret").build()))
            .secretVariableDTO(SecretVariableDTO.builder()
                                   .type(SecretVariableDTO.Type.TEXT)
                                   .name(secretName)
                                   .secret(SecretRefData.builder().identifier(secretKey).build())
                                   .build())
            .build());

    Map<String, SecretVarParams> secretEnvVars = new HashMap<>();
    secretEnvVars.put(secretVar, SecretVarParams.builder().secretKey(secretKey).secretName(secretName).build());
    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageWithoutCred).build())
        .commands(commands)
        .args(args)
        .envVars(envVars)
        .workingDir(workingDir)
        .ports(Collections.singletonList(port))
        .containerSecrets(ContainerSecrets.builder().secretVariableDetails(secretVariableDetailsList).build())
        .secretEnvVars(secretEnvVars)
        .build();
  }

  public static ContainerSpecBuilderResponse basicCreateSpecWithSecretEnvPortWorkingDirResponse() {
    Map<String, String> envVars = new HashMap<>();
    List<V1EnvVar> ctrEnvVars = new ArrayList<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    envVars.forEach((name, val) -> ctrEnvVars.add(new V1EnvVarBuilder().withName(name).withValue(val).build()));

    ctrEnvVars.add(
        new V1EnvVarBuilder()
            .withName(secretVar)
            .withValueFrom(
                new V1EnvVarSourceBuilder()
                    .withSecretKeyRef(new V1SecretKeySelectorBuilder().withKey(secretKey).withName(secretName).build())
                    .build())
            .build());

    V1ContainerPort containerPort = new V1ContainerPortBuilder().withContainerPort(port).build();
    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withEnv(ctrEnvVars)
                                     .withWorkingDir(workingDir)
                                     .withPorts(Arrays.asList(containerPort))
                                     .withSecurityContext(securityContext)
                                     .withVolumeMounts(new ArrayList<>());
    return ContainerSpecBuilderResponse.builder().containerBuilder(builder).volumes(new ArrayList<>()).build();
  }

  public static CIK8ContainerParams createSpecWithVolumeMountInput() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    ImageDetails imageWithoutCred = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();

    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(volume1, mountPath1);
    volumeToMountPath.put(volume2, mountPath2);

    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageWithoutCred).build())
        .commands(commands)
        .args(args)
        .envVars(envVars)
        .volumeToMountPath(volumeToMountPath)
        .build();
  }

  public static ContainerSpecBuilderResponse createSpecWithVolumeMountResponse() {
    Map<String, String> envVars = new HashMap<>();
    List<V1EnvVar> ctrEnvVars = new ArrayList<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    envVars.forEach((name, val) -> ctrEnvVars.add(new V1EnvVarBuilder().withName(name).withValue(val).build()));

    List<V1VolumeMount> ctrVolumeMounts = new ArrayList<>();
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume1).withMountPath(mountPath1).build());
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume2).withMountPath(mountPath2).build());

    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withEnv(ctrEnvVars)
                                     .withVolumeMounts(ctrVolumeMounts)
                                     .withSecurityContext(securityContext)
                                     .withPorts(new ArrayList<>());

    return ContainerSpecBuilderResponse.builder().containerBuilder(builder).volumes(new ArrayList<>()).build();
  }

  public static CIK8ContainerParams createSpecWithImageCredInput() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    ImageDetails imageDetailsWithCred = ImageDetails.builder()
                                            .name(imageName)
                                            .tag(tag)
                                            .registryUrl(registryUrl)
                                            .username(userName)
                                            .password(password)
                                            .build();
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(volume1, mountPath1);
    volumeToMountPath.put(volume2, mountPath2);

    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithCred).build())
        .commands(commands)
        .args(args)
        .envVars(envVars)
        .volumeToMountPath(volumeToMountPath)
        .imageSecret(imageSecret)
        .build();
  }

  public static ContainerSpecBuilderResponse createSpecWithImageCredResponse() {
    Map<String, String> envVars = new HashMap<>();
    List<V1EnvVar> ctrEnvVars = new ArrayList<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    envVars.forEach((name, val) -> ctrEnvVars.add(new V1EnvVarBuilder().withName(name).withValue(val).build()));

    List<V1VolumeMount> ctrVolumeMounts = new ArrayList<>();
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume1).withMountPath(mountPath1).build());
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume2).withMountPath(mountPath2).build());

    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withEnv(ctrEnvVars)
                                     .withVolumeMounts(ctrVolumeMounts)
                                     .withSecurityContext(securityContext)
                                     .withPorts(new ArrayList<>());
    return ContainerSpecBuilderResponse.builder()
        .containerBuilder(builder)
        .imageSecret(new V1LocalObjectReferenceBuilder().withName(imageSecret).build())
        .volumes(new ArrayList<>())
        .build();
  }

  public static CIK8ContainerParams createSpecWithResourcesCredInput() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    ImageDetails imageDetailsWithCred = ImageDetails.builder()
                                            .name(imageName)
                                            .tag(tag)
                                            .registryUrl(registryUrl)
                                            .username(userName)
                                            .password(password)
                                            .build();
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(volume1, mountPath1);
    volumeToMountPath.put(volume2, mountPath2);

    ContainerResourceParams resourceParams = ContainerResourceParams.builder()
                                                 .resourceLimitMemoryMiB(limitMemoryMib)
                                                 .resourceRequestMemoryMiB(requestMemoryMib)
                                                 .resourceLimitMilliCpu(limitMilliCpu)
                                                 .resourceRequestMilliCpu(requestMilliCpu)
                                                 .build();

    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithCred).build())
        .commands(commands)
        .args(args)
        .envVars(envVars)
        .volumeToMountPath(volumeToMountPath)
        .containerResourceParams(resourceParams)
        .imageSecret(imageSecret)
        .build();
  }

  public static ContainerSpecBuilderResponse createSpecWithResourcesResponse() {
    Map<String, String> envVars = new HashMap<>();
    List<V1EnvVar> ctrEnvVars = new ArrayList<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    envVars.forEach((name, val) -> ctrEnvVars.add(new V1EnvVarBuilder().withName(name).withValue(val).build()));

    List<V1VolumeMount> ctrVolumeMounts = new ArrayList<>();
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume1).withMountPath(mountPath1).build());
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume2).withMountPath(mountPath2).build());

    Quantity ctrRequestMemoryMib = new Quantity(format("%d%s", requestMemoryMib, CIConstants.MEMORY_FORMAT));
    Quantity ctrLimitMemoryMib = new Quantity(format("%d%s", limitMemoryMib, CIConstants.MEMORY_FORMAT));
    Quantity ctrRequestMilliCpu = new Quantity(format("%d%s", requestMilliCpu, CIConstants.CPU_FORMAT));
    Quantity ctrLimitMilliCpu = new Quantity(format("%d%s", limitMilliCpu, CIConstants.CPU_FORMAT));
    V1ResourceRequirements resourceRequirements = new V1ResourceRequirementsBuilder()
                                                      .addToLimits(CIConstants.MEMORY, ctrLimitMemoryMib)
                                                      .addToRequests(CIConstants.MEMORY, ctrRequestMemoryMib)
                                                      .addToRequests(CIConstants.CPU, ctrRequestMilliCpu)
                                                      .addToLimits(CIConstants.CPU, ctrLimitMilliCpu)
                                                      .build();

    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withEnv(ctrEnvVars)
                                     .withVolumeMounts(ctrVolumeMounts)
                                     .withResources(resourceRequirements)
                                     .withSecurityContext(securityContext)
                                     .withPorts(new ArrayList<>());
    return ContainerSpecBuilderResponse.builder()
        .containerBuilder(builder)
        .imageSecret(new V1LocalObjectReferenceBuilder().withName(imageSecret).build())
        .volumes(new ArrayList<>())
        .build();
  }

  public static CIK8ContainerParams createSpecWithSecretVolumes() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    ImageDetails imageDetailsWithCred = ImageDetails.builder()
                                            .name(imageName)
                                            .tag(tag)
                                            .registryUrl(registryUrl)
                                            .username(userName)
                                            .password(password)
                                            .build();
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(volume1, mountPath1);
    volumeToMountPath.put(volume2, mountPath2);

    Map<String, SecretVolumeParams> secretVolumes = new HashMap<>();
    secretVolumes.put(secretKey,
        SecretVolumeParams.builder()
            .mountPath(secretVolumeMountPath)
            .secretKey(secretKey)
            .secretName(secretName)
            .build());

    return CIK8ContainerParams.builder()
        .name(containerName)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder().imageDetails(imageDetailsWithCred).build())
        .secretVolumes(secretVolumes)
        .commands(commands)
        .args(args)
        .envVars(envVars)
        .volumeToMountPath(volumeToMountPath)
        .imageSecret(imageSecret)
        .build();
  }

  public static ContainerSpecBuilderResponse createSpecWithSecretVolumesResponse() {
    Map<String, String> envVars = new HashMap<>();
    List<V1EnvVar> ctrEnvVars = new ArrayList<>();
    envVars.put(var1, value1);
    envVars.put(var2, value2);
    envVars.forEach((name, val) -> ctrEnvVars.add(new V1EnvVarBuilder().withName(name).withValue(val).build()));

    List<V1VolumeMount> ctrVolumeMounts = new ArrayList<>();
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume1).withMountPath(mountPath1).build());
    ctrVolumeMounts.add(new V1VolumeMountBuilder().withName(volume2).withMountPath(mountPath2).build());
    ctrVolumeMounts.add(
        new V1VolumeMountBuilder().withName(secretsVolumeName).withMountPath(secretVolumeMountPath).build());

    List<V1Volume> ctrVolumes = new ArrayList<>();
    ctrVolumes.add(new V1VolumeBuilder()
                       .withName(secretsVolumeName)
                       .withSecret(new V1SecretVolumeSourceBuilder()
                                       .withSecretName(secretName)
                                       .withItems(new V1KeyToPathBuilder()
                                                      .withKey(secretKey)
                                                      .withMode(CIConstants.SECRET_FILE_MODE)
                                                      .withPath(secretKey)
                                                      .build())
                                       .build())
                       .build());

    V1ContainerBuilder builder = new V1ContainerBuilder()
                                     .withName(containerName)
                                     .withArgs(args)
                                     .withCommand(commands)
                                     .withImage(imageCtrName)
                                     .withEnv(ctrEnvVars)
                                     .withVolumeMounts(ctrVolumeMounts)
                                     .withSecurityContext(securityContext)
                                     .withPorts(new ArrayList<>());
    return ContainerSpecBuilderResponse.builder()
        .containerBuilder(builder)
        .imageSecret(new V1LocalObjectReferenceBuilder().withName(imageSecret).build())
        .volumes(ctrVolumes)
        .build();
  }
}
