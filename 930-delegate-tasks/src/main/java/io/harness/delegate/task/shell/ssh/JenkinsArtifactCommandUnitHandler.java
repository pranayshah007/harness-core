package io.harness.delegate.task.shell.ssh;

import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.JenkinsConfig;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.service.impl.jenkins.JenkinsUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class JenkinsArtifactCommandUnitHandler extends ArtifactCommandUnitHandler {
  private final JenkinsUtils jenkinsUtil;
  private final SecretDecryptionService secretDecryptionService;

  @Inject
  public JenkinsArtifactCommandUnitHandler(JenkinsUtils jenkinsUtil, SecretDecryptionService secretDecryptionService) {
    this.jenkinsUtil = jenkinsUtil;
    this.secretDecryptionService = secretDecryptionService;
  }

  @Override
  protected InputStream downloadFromRemoteRepo(SshExecutorFactoryContext context, LogCallback logCallback)
      throws IOException {
    Pair<String, InputStream> pair = null;
    try {
      if (context.getArtifactDelegateConfig() instanceof JenkinsArtifactDelegateConfig) {
        Jenkins jenkins = configureJenkins(context);
        JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig =
            (JenkinsArtifactDelegateConfig) context.getArtifactDelegateConfig();
        updateArtifactMetadata(context);
        logCallback.saveExecutionLog(color(
            format("Downloading jenkins artifact with identifier: %s", jenkinsArtifactDelegateConfig.getIdentifier()),
            White, Bold));
        logCallback.saveExecutionLog("Jenkins Artifact Path: " + jenkinsArtifactDelegateConfig.getArtifactPath());
        pair = jenkins.downloadArtifact(jenkinsArtifactDelegateConfig.getJobName(),
            jenkinsArtifactDelegateConfig.getBuild(), jenkinsArtifactDelegateConfig.getArtifactPath());
      }
    } catch (URISyntaxException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Failure in downloading jenkins artifact ", sanitizedException);
      logCallback.saveExecutionLog(
          "Failed to download jenkins artifact. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
          CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(
          format(SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_HINT),
          format(SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION,
              context.getArtifactDelegateConfig().getIdentifier()),
          new SshCommandExecutionException(format(SshExceptionConstants.JENKINS_ARTIFACT_DOWNLOAD_FAILED,
              context.getArtifactDelegateConfig().getIdentifier())));
    }
    if (null != pair) {
      return pair.getRight();
    } else {
      return null;
    }
  }

  @Override
  public Long getArtifactSize(SshExecutorFactoryContext context, LogCallback logCallback) {
    if (context.getArtifactDelegateConfig() instanceof JenkinsArtifactDelegateConfig) {
      Jenkins jenkins = configureJenkins(context);
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig =
          (JenkinsArtifactDelegateConfig) context.getArtifactDelegateConfig();
      updateArtifactMetadata(context);
      return jenkins.getFileSize(jenkinsArtifactDelegateConfig.getJobName(), jenkinsArtifactDelegateConfig.getBuild(),
          jenkinsArtifactDelegateConfig.getArtifactPath());
    } else {
      return 0L;
    }
  }

  private Jenkins configureJenkins(SshExecutorFactoryContext context) {
    JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig =
        (JenkinsArtifactDelegateConfig) context.getArtifactDelegateConfig();
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    JenkinsAuthType authType = jenkinsConnectorDto.getAuth().getAuthType();
    Jenkins jenkins = null;
    if (JenkinsAuthType.USER_PASSWORD.equals(authType)) {
      JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO =
          (JenkinsUserNamePasswordDTO) jenkinsConnectorDto.getAuth().getCredentials();
      JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                        .jenkinsUrl(jenkinsConnectorDto.getJenkinsUrl())
                                        .username(jenkinsUserNamePasswordDTO.getUsername())
                                        .password((jenkinsUserNamePasswordDTO.isDecrypted()
                                                ? jenkinsUserNamePasswordDTO
                                                : decrypt(jenkinsUserNamePasswordDTO, jenkinsArtifactDelegateConfig))
                                                      .getPasswordRef()
                                                      .getDecryptedValue())
                                        .build();
      jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    } else if (JenkinsAuthType.BEARER_TOKEN.equals(authType)) {
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO =
          (JenkinsBearerTokenDTO) jenkinsConnectorDto.getAuth().getCredentials();
      JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                        .jenkinsUrl(jenkinsConnectorDto.getJenkinsUrl())
                                        .token((jenkinsBearerTokenDTO.isDecrypted()
                                                ? jenkinsBearerTokenDTO
                                                : decrypt(jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig))
                                                   .getTokenRef()
                                                   .getDecryptedValue())
                                        .build();
      jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    }
    return jenkins;
  }

  private String getJenkinsUrl(SshExecutorFactoryContext context) {
    JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig =
        (JenkinsArtifactDelegateConfig) context.getArtifactDelegateConfig();
    JenkinsConnectorDTO jenkinsConnectorDto =
        (JenkinsConnectorDTO) jenkinsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    return jenkinsConnectorDto.getJenkinsUrl();
  }

  private void updateArtifactMetadata(SshExecutorFactoryContext context) {
    JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig =
        (JenkinsArtifactDelegateConfig) context.getArtifactDelegateConfig();
    Map<String, String> artifactMetadata = context.getArtifactMetadata();
    String artifactPath = Paths
                              .get(getJenkinsUrl(context), jenkinsArtifactDelegateConfig.getJobName(),
                                  jenkinsArtifactDelegateConfig.getArtifactPath())
                              .toString();
    artifactMetadata.put(io.harness.artifact.ArtifactMetadataKeys.artifactPath, artifactPath);
    artifactMetadata.put(ArtifactMetadataKeys.artifactName, artifactPath);
  }

  private JenkinsUserNamePasswordDTO decrypt(JenkinsUserNamePasswordDTO jenkinsUserNamePasswordDTO,
      JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig) {
    return (JenkinsUserNamePasswordDTO) secretDecryptionService.decrypt(
        jenkinsUserNamePasswordDTO, jenkinsArtifactDelegateConfig.getEncryptedDataDetails());
  }

  private JenkinsBearerTokenDTO decrypt(
      JenkinsBearerTokenDTO jenkinsBearerTokenDTO, JenkinsArtifactDelegateConfig jenkinsArtifactDelegateConfig) {
    return (JenkinsBearerTokenDTO) secretDecryptionService.decrypt(
        jenkinsBearerTokenDTO, jenkinsArtifactDelegateConfig.getEncryptedDataDetails());
  }
}
