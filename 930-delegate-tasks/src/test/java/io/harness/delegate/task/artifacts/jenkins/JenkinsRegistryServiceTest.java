/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.artifacts.jenkins.service.JenkinsRegistryService;
import io.harness.artifacts.jenkins.service.JenkinsRegistryUtils;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JenkinsRegistryServiceTest extends CategoryTest {
  @Mock private JenkinsRegistryUtils jenkinsRegistryUtils;

  @Mock Http http;

  @InjectMocks private JenkinsRegistryService jenkinsRegistryService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJobs() throws Exception {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JobDetails jobDetails = new JobDetails();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    when(jenkinsRegistryUtils.getJobs(jenkinsInternalConfig, null)).thenReturn(Collections.singletonList(jobDetails));
    List<JobDetails> jobDetails1 = jenkinsRegistryService.getJobs(jenkinsInternalConfig, null);
    assertThat(jobDetails1).isNotNull();

    verify(jenkinsRegistryUtils).getJobs(jenkinsInternalConfig, null);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJobWithDetails() {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JobWithDetails jobDetails = new JobWithDetails();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    when(jenkinsRegistryUtils.getJobWithDetails(jenkinsInternalConfig, null)).thenReturn(jobDetails);
    JobWithDetails jobDetails1 = jenkinsRegistryService.getJobWithDetails(jenkinsInternalConfig, null);
    assertThat(jobDetails1).isNotNull();

    verify(jenkinsRegistryUtils).getJobWithDetails(jenkinsInternalConfig, null);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBuildsForJob() throws IOException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    when(jenkinsRegistryUtils.getBuildsForJob(
             jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25))
        .thenReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().build()));
    List<BuildDetails> buildDetails = jenkinsRegistryService.getBuildsForJob(
        jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25);
    assertThat(buildDetails).isNotNull();

    verify(jenkinsRegistryUtils)
        .getBuildsForJob(jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testVerifyBuild() throws IOException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    when(jenkinsRegistryUtils.verifyBuildForJob(
             jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), "tag"))
        .thenReturn(BuildDetails.Builder.aBuildDetails().build());
    BuildDetails buildDetails = jenkinsRegistryService.verifyBuildForJob(
        jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), "tag");
    assertThat(buildDetails).isNotNull();
    verify(jenkinsRegistryUtils)
        .verifyBuildForJob(jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), "tag");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBuildsForJobException() {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    try {
      when(jenkinsRegistryUtils.getBuildsForJob(
               jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25))
          .thenThrow(WingsException.class);
      jenkinsRegistryService.getBuildsForJob(
          jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(WingsException.class);
    }
    try {
      when(jenkinsRegistryUtils.getBuildsForJob(
               jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25))
          .thenThrow(IOException.class);
      jenkinsRegistryService.getBuildsForJob(
          jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(WingsException.class);
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBuildsForJobHintException() {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    try {
      when(jenkinsRegistryUtils.getBuildsForJob(
               jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25))
          .thenThrow(IOException.class);
      jenkinsRegistryService.getBuildsForJob(
          jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(HintException.class);
      assertThat(exception.getMessage()).isEqualTo("Failed to fetch build details jenkins server ");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBuildsForJobForException() {
    try {
      String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
      JenkinsConnectorDTO jenkinsConnectorDTO =
          JenkinsConnectorDTO.builder()
              .jenkinsUrl("https://Jenkins.com")
              .auth(JenkinsAuthenticationDTO.builder()
                        .authType(JenkinsAuthType.USER_PASSWORD)
                        .credentials(JenkinsUserNamePasswordDTO.builder()
                                         .username("CDC")
                                         .passwordRef(SecretRefData.builder()
                                                          .identifier("secret-ref")
                                                          .decryptedValue("This is a secret".toCharArray())
                                                          .build())
                                         .build())
                        .build())
              .build();
      JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
          JenkinsArtifactDelegateRequest.builder()
              .artifactPaths(Collections.singletonList("artifactPath"))
              .jobName(jobName)
              .jenkinsConnectorDTO(jenkinsConnectorDTO)
              .buildNumber("tag")
              .build();
      JenkinsInternalConfig jenkinsInternalConfig =
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
      when(jenkinsRegistryUtils.getBuildsForJob(
               jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25))
          .thenReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().build()));
      List<BuildDetails> buildDetails = jenkinsRegistryService.getBuildsForJob(
          jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"), 25);
    } catch (Exception ex) {
      assertThat(ex.getMessage())
          .isEqualTo(
              "Check if the permissions are scoped for the authenticated user & check if the right connector chosen for fetching the Jobs.");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testLastSuccessfulBuildForJob() throws IOException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    when(jenkinsRegistryUtils.getLastSuccessfulBuildForJob(
             jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath")))
        .thenReturn(BuildDetails.Builder.aBuildDetails().build());
    BuildDetails buildDetails = jenkinsRegistryService.getLastSuccessfulBuildForJob(
        jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"));
    assertThat(buildDetails).isNotNull();

    verify(jenkinsRegistryUtils)
        .getLastSuccessfulBuildForJob(jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testLastSuccessfulBuildForJobException() throws IOException {
    try {
      String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
      JenkinsConnectorDTO jenkinsConnectorDTO =
          JenkinsConnectorDTO.builder()
              .jenkinsUrl("https://Jenkins.com")
              .auth(JenkinsAuthenticationDTO.builder()
                        .authType(JenkinsAuthType.USER_PASSWORD)
                        .credentials(JenkinsUserNamePasswordDTO.builder()
                                         .username("CDC")
                                         .passwordRef(SecretRefData.builder()
                                                          .identifier("secret-ref")
                                                          .decryptedValue("This is a secret".toCharArray())
                                                          .build())
                                         .build())
                        .build())
              .build();
      JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
          JenkinsArtifactDelegateRequest.builder()
              .artifactPaths(Collections.singletonList("artifactPath"))
              .jobName(jobName)
              .jenkinsConnectorDTO(jenkinsConnectorDTO)
              .buildNumber("tag")
              .build();
      JenkinsInternalConfig jenkinsInternalConfig =
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
      when(jenkinsRegistryUtils.getLastSuccessfulBuildForJob(
               jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath")))
          .thenThrow(IOException.class);
      BuildDetails buildDetails = jenkinsRegistryService.getLastSuccessfulBuildForJob(
          jenkinsInternalConfig, jobName, Collections.singletonList("artifactPath"));
    } catch (Exception ex) {
      assertThat(ex.getMessage())
          .isEqualTo(
              "Check if the permissions are scoped for the authenticated user & check if the right connector chosen for fetching the Jobs.");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testJobWithParamters() throws IOException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    JobDetails jobDetails = new JobDetails();
    when(jenkinsRegistryUtils.getJobWithParamters(jobName, jenkinsInternalConfig)).thenReturn(jobDetails);
    JobDetails jobDetails1 = jenkinsRegistryService.getJobWithParamters(jenkinsInternalConfig, jobName);
    assertThat(jobDetails1).isNotNull();

    verify(jenkinsRegistryUtils).getJobWithParamters(jobName, jenkinsInternalConfig);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testValidateCredentials() throws IOException {
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    JenkinsConnectorDTO jenkinsConnectorDTO =
        JenkinsConnectorDTO.builder()
            .jenkinsUrl("https://Jenkins.com")
            .auth(JenkinsAuthenticationDTO.builder()
                      .authType(JenkinsAuthType.USER_PASSWORD)
                      .credentials(JenkinsUserNamePasswordDTO.builder()
                                       .username("CDC")
                                       .passwordRef(SecretRefData.builder()
                                                        .identifier("secret-ref")
                                                        .decryptedValue("This is a secret".toCharArray())
                                                        .build())
                                       .build())
                      .build())
            .build();
    JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest =
        JenkinsArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .jobName(jobName)
            .jenkinsConnectorDTO(jenkinsConnectorDTO)
            .buildNumber("tag")
            .build();
    JenkinsInternalConfig jenkinsInternalConfig =
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsArtifactDelegateRequest);
    try {
      jenkinsRegistryService.validateCredentials(jenkinsInternalConfig);
    } catch (io.harness.exception.HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Could not reach Jenkins Server at :https://Jenkins.com");
    }

    JenkinsInternalConfig jenkinsInternalConfig1 = JenkinsInternalConfig.builder().authMechanism("AUTH").build();
    try {
      jenkinsRegistryService.validateCredentials(jenkinsInternalConfig1);
    } catch (io.harness.exception.HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("UserName/Password should be not empty");
    }
    jenkinsInternalConfig1 =
        JenkinsInternalConfig.builder().token("".toCharArray()).authMechanism(JenkinsRegistryUtils.TOKEN_FIELD).build();
    try {
      jenkinsRegistryService.validateCredentials(jenkinsInternalConfig1);
    } catch (io.harness.exception.HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Token should not be empty");
    }

    jenkinsInternalConfig1 = JenkinsInternalConfig.builder()
                                 .token("token".toCharArray())
                                 .jenkinsUrl("https://Jenkins.com")
                                 .authMechanism(JenkinsRegistryUtils.TOKEN_FIELD)
                                 .build();
    try {
      jenkinsRegistryService.validateCredentials(jenkinsInternalConfig1);
    } catch (io.harness.exception.HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Could not reach Jenkins Server at :https://Jenkins.com");
    }
  }
}
