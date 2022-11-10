/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.AwsConfig.AwsConfigBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConstants;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ScriptType;

import software.wings.beans.AWSTemporaryCredentials;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.command.AWS4SignerForAuthorizationHeader;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class AwsS3ArtifactDownloadHandler implements ArtifactDownloadHandler {
  private static final Map<String, String> bucketRegions = new ConcurrentHashMap<>();
  private static final String ISO_8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
  private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final String AWS_CREDENTIALS_URL = "http://169.254.169.254/";

  @Inject private EncryptionService encryptionService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType) {
    AwsS3ArtifactDelegateConfig awsS3ArtifactDelegateConfig = (AwsS3ArtifactDelegateConfig) artifactDelegateConfig;
    if (ScriptType.POWERSHELL.equals(scriptType)) {
      return getPowerShellCommand(awsS3ArtifactDelegateConfig, destinationPath);
    } else if (ScriptType.BASH.equals(scriptType)) {
      return getSshCommand(awsS3ArtifactDelegateConfig, destinationPath);
    } else {
      throw new InvalidRequestException("Unknown script type.");
    }
  }

  public String getPowerShellCommand(AwsS3ArtifactDelegateConfig s3ArtifactDelegateConfig, String destinationPath) {
    AwsConnectionDetails connectionDetails = mapAwsConnectionDetails(s3ArtifactDelegateConfig, destinationPath);
    return "$Headers = @{\n"
        + "    Authorization = \"" + connectionDetails.authorizationHeader + "\"\n"
        + "    \"x-amz-content-sha256\" = \"" + EMPTY_BODY_SHA256 + "\"\n"
        + "    \"x-amz-date\" = \"" + connectionDetails.dateTimeStamp + "\"\n"
        + (isEmpty(connectionDetails.awsToken) ? ""
                                               : " \"x-amz-security-token\" = \"" + connectionDetails.awsToken + "\"\n")
        + "}\n"
        + " $ProgressPreference = 'SilentlyContinue'\n"
        + " Invoke-WebRequest -Uri \"" + connectionDetails.awsEndpointUrl
        + "\" -Headers $Headers -OutFile (New-Item -Path \"" + connectionDetails.targetPath + "\\"
        + connectionDetails.artifactFileName + "\""
        + " -Force)";
  }

  public String getSshCommand(AwsS3ArtifactDelegateConfig s3ArtifactDelegateConfig, String destinationPath) {
    AwsConnectionDetails connectionDetails = mapAwsConnectionDetails(s3ArtifactDelegateConfig, destinationPath);
    return "curl --fail \"" + connectionDetails.awsEndpointUrl + "\""
        + " \\\n"
        + "-H \"Authorization: " + connectionDetails.authorizationHeader + "\" \\\n"
        + "-H \"x-amz-content-sha256: " + EMPTY_BODY_SHA256 + "\" \\\n"
        + "-H \"x-amz-date: " + connectionDetails.dateTimeStamp + "\" \\\n"
        + (isEmpty(connectionDetails.awsToken) ? ""
                                               : "-H \"x-amz-security-token: " + connectionDetails.awsToken + "\" \\\n")
        + " -o \"" + connectionDetails.targetPath + "/" + connectionDetails.artifactFileName + "\"";
  }

  private AwsConnectionDetails mapAwsConnectionDetails(
      AwsS3ArtifactDelegateConfig s3ArtifactDelegateConfig, String targetPath) {
    if (isEmpty(bucketRegions)) {
      initBucketRegions();
    }
    AwsConfig awsConfigDecrypted = (AwsConfig) encryptionService.decrypt(
        composeAwsConfig(s3ArtifactDelegateConfig), s3ArtifactDelegateConfig.getEncryptionDetails(), false);
    String awsAccessKey;
    String awsSecretKey;
    String awsToken = null;
    if (awsConfigDecrypted.isUseEc2IamCredentials()) {
      AWSTemporaryCredentials credentials =
          awsHelperService.getCredentialsForIAMROleOnDelegate(AWS_CREDENTIALS_URL, awsConfigDecrypted);
      awsAccessKey = credentials.getAccessKeyId();
      awsSecretKey = credentials.getSecretKey();
      awsToken = credentials.getToken();
    } else {
      awsAccessKey = String.valueOf(awsConfigDecrypted.getAccessKey());
      awsSecretKey = String.valueOf(awsConfigDecrypted.getSecretKey());
    }
    String bucketName = s3ArtifactDelegateConfig.getBucketName();
    if (isEmpty(bucketName)) {
      throw new InvalidRequestException("Bucket name needs to be defined");
    }
    String region = awsHelperService.getBucketRegion(
        awsConfigDecrypted, s3ArtifactDelegateConfig.getEncryptionDetails(), bucketName);
    String artifactPath = s3ArtifactDelegateConfig.getArtifactPath();
    if (isEmpty(artifactPath)) {
      throw new InvalidRequestException("Artifact path needs to be defined");
    }

    URL endpointUrl;
    String url = getAmazonS3Url(bucketName, region, artifactPath);
    try {
      endpointUrl = new URL(url);
    } catch (MalformedURLException e) {
      throw new InvalidRequestException("Unable to parse service endpoint: ", e);
    }

    Date now = new Date();
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(ISO_8601_BASIC_FORMAT);
    dateTimeFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    String dateTimeStamp = dateTimeFormat.format(now);
    String authorizationHeader = AWS4SignerForAuthorizationHeader.getAWSV4AuthorizationHeader(
        endpointUrl, region, awsAccessKey, awsSecretKey, now, awsToken);
    String awsEndpointUrl =
        AWS4SignerForAuthorizationHeader.getEndpointWithCanonicalizedResourcePath(endpointUrl, true);
    String artifactFileName = Paths.get(artifactPath).getFileName().toString();
    return new AwsConnectionDetails(
        dateTimeStamp, authorizationHeader, awsToken, awsEndpointUrl, targetPath, artifactFileName);
  }

  private String getAmazonS3Url(String bucketName, String region, String artifactPath) {
    return "https://" + bucketName + ".s3" + bucketRegions.get(region) + ".amazonaws.com"
        + "/" + artifactPath;
  }

  private void initBucketRegions() {
    bucketRegions.put("us-east-2", "-us-east-2");
    bucketRegions.put("us-east-1", "");
    bucketRegions.put("us-west-1", "-us-west-1");
    bucketRegions.put("us-west-2", "-us-west-2");
    bucketRegions.put("ca-central-1", "-ca-central-1");
    bucketRegions.put("ap-east-1", "-ap-east-1");
    bucketRegions.put("ap-south-1", "-ap-south-1");
    bucketRegions.put("ap-northeast-2", "-ap-northeast-2");
    bucketRegions.put("ap-northeast-3", "-ap-northeast-3");
    bucketRegions.put("ap-southeast-1", "-ap-southeast-1");
    bucketRegions.put("ap-southeast-2", "-ap-southeast-2");
    bucketRegions.put("ap-northeast-1", "-ap-northeast-1");
    bucketRegions.put("cn-north-1", ".cn-north-1");
    bucketRegions.put("cn-northwest-1", ".cn-northwest-1");
    bucketRegions.put("eu-central-1", "-eu-central-1");
    bucketRegions.put("eu-west-1", "-eu-west-1");
    bucketRegions.put("eu-west-2", "-eu-west-2");
    bucketRegions.put("eu-west-3", "-eu-west-3");
    bucketRegions.put("eu-north-1", "-eu-north-1");
    bucketRegions.put("eu-south-1", "-eu-south-1");
    bucketRegions.put("sa-east-1", "-sa-east-1");
    bucketRegions.put("me-south-1", "-me-south-1");
    bucketRegions.put("me-central-1", "-me-central-1");
    bucketRegions.put("us-gov-east-1", "-us-gov-east-1");
    bucketRegions.put("us-gov-west-1", "-us-gov-west-1");
  }

  private AwsConfig composeAwsConfig(AwsS3ArtifactDelegateConfig s3ArtifactDelegateConfig) {
    if (s3ArtifactDelegateConfig == null || s3ArtifactDelegateConfig.getAwsConnector() == null) {
      throw new InvalidRequestException("AWS S3 artifact Delegate config and AWS S3 connector need to be defined.");
    }
    final AwsConfigBuilder configBuilder = AwsConfig.builder().accountId(s3ArtifactDelegateConfig.getAccountId());
    AwsCredentialDTO awsCredentialDTO = s3ArtifactDelegateConfig.getAwsConnector().getCredential();
    if (awsCredentialDTO != null && awsCredentialDTO.getAwsCredentialType() != null) {
      AwsCredentialType credentialType = awsCredentialDTO.getAwsCredentialType();
      switch (credentialType.getDisplayName()) {
        case AwsConstants.INHERIT_FROM_DELEGATE: {
          configBuilder.useEc2IamCredentials(true);
          configBuilder.useIRSA(false);
          configBuilder.tag(((AwsInheritFromDelegateSpecDTO) awsCredentialDTO.getConfig())
                                .getDelegateSelectors()
                                .stream()
                                .findAny()
                                .orElse(null));
        } break;
        case AwsConstants.MANUAL_CONFIG: {
          configBuilder.useEc2IamCredentials(false);
          configBuilder.useIRSA(false);

          AwsManualConfigSpecDTO decryptedSpec = (AwsManualConfigSpecDTO) secretDecryptionService.decrypt(
              awsCredentialDTO.getConfig(), s3ArtifactDelegateConfig.getEncryptionDetails());
          configBuilder.accessKey(decryptedSpec.getAccessKey() != null
                  ? decryptedSpec.getAccessKey().toCharArray()
                  : decryptedSpec.getAccessKeyRef().getDecryptedValue());
          configBuilder.secretKey(decryptedSpec.getSecretKeyRef().getDecryptedValue());
          configBuilder.useEncryptedAccessKey(false);
        } break;
        case AwsConstants.IRSA: {
          configBuilder.useEc2IamCredentials(false);
          configBuilder.useEncryptedAccessKey(false);
          configBuilder.useIRSA(true);
        } break;
        default:
          throw new InvalidRequestException("Invalid credentials type");
      }
      if (s3ArtifactDelegateConfig.getAwsConnector().getCredential().getCrossAccountAccess() != null) {
        AwsCrossAccountAttributes awsCrossAccountAttributes =
            AwsCrossAccountAttributes.builder()
                .crossAccountRoleArn(s3ArtifactDelegateConfig.getAwsConnector()
                                         .getCredential()
                                         .getCrossAccountAccess()
                                         .getCrossAccountRoleArn())
                .externalId(
                    s3ArtifactDelegateConfig.getAwsConnector().getCredential().getCrossAccountAccess().getExternalId())
                .build();
        configBuilder.crossAccountAttributes(awsCrossAccountAttributes);
      }
    } else {
      throw new InvalidRequestException("No credentialsType provided with the request.");
    }
    AwsConfig awsConfig = configBuilder.build();
    awsConfig.setCertValidationRequired(s3ArtifactDelegateConfig.isCertValidationRequired());
    return awsConfig;
  }

  @AllArgsConstructor
  private static final class AwsConnectionDetails {
    private String dateTimeStamp;
    private String authorizationHeader;
    private String awsToken;
    private String awsEndpointUrl;
    private String targetPath;
    private String artifactFileName;
  }
}
