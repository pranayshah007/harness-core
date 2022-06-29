/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.Log.Builder.aLog;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.nexus.NexusRequest;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;

import software.wings.beans.AWSTemporaryCredentials;
import software.wings.beans.AwsConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.SftpConfig;
import software.wings.beans.SmbConfig;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackageFileInfo;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.azure.devops.AzureArtifactsServiceHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.nexus.NexusTwoServiceImpl;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.SftpHelperService;
import software.wings.service.impl.SmbHelperService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.NexusConfigToNexusRequestMapper;
import software.wings.utils.RepositoryType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@JsonTypeName("DOWNLOAD_ARTIFACT")
@EqualsAndHashCode(callSuper = true)
@Slf4j
@TargetModule(HarnessModule._950_COMMAND_LIBRARY_COMMON)
public class DownloadArtifactCommandUnit extends ExecCommandUnit {
  private static final String NO_ARTIFACTS_ERROR_STRING = "There are no artifacts to download";
  private static final String ISO_8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
  private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final String PERIOD_DELIMITER = ".";

  @Inject private EncryptionService encryptionService;
  @Inject @Transient private transient DelegateLogService delegateLogService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private SmbHelperService smbHelperService;
  @Inject private SftpHelperService sftpHelperService;
  @Inject private AzureArtifactsService azureArtifactsService;
  @Inject private NexusTwoServiceImpl nexusTwoService;

  private static Map<String, String> bucketRegions = new HashMap<>();

  private String artifactVariableName = ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME;

  public String getArtifactVariableName() {
    return artifactVariableName == null ? ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME : artifactVariableName;
  }

  public void setArtifactVariableName(String artifactVariableName) {
    this.artifactVariableName = artifactVariableName;
  }

  /**
   * Instantiates a new Download Artifact command unit.
   */
  public DownloadArtifactCommandUnit() {
    setCommandUnitType(CommandUnitType.DOWNLOAD_ARTIFACT);
    initBucketRegions();
    setArtifactNeeded(true);
  }

  @SchemaIgnore
  @Override
  public void updateServiceArtifactVariableNames(Set<String> serviceArtifactVariableNames) {
    serviceArtifactVariableNames.add(getArtifactVariableName());
  }

  @Override
  protected CommandExecutionStatus executeInternal(ShellCommandExecutionContext context) {
    if (StringUtils.isEmpty(getCommandPath())) {
      saveExecutionLog(context, ERROR, "Artifact Download Directory cannot be null or empty");
      throw new InvalidRequestException("Artifact Download Directory cannot be null or empty", USER);
    }
    List<EncryptedDataDetail> encryptionDetails;
    ArtifactStreamAttributes artifactStreamAttributes;
    Map<String, String> metadata;
    if (context.isMultiArtifact()) {
      Map<String, Artifact> multiArtifactMap = context.getMultiArtifactMap();
      Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap = context.getArtifactStreamAttributesMap();
      Map<String, List<EncryptedDataDetail>> encryptedDataDetailsMap =
          context.getArtifactServerEncryptedDataDetailsMap();
      Artifact artifact = multiArtifactMap.get(artifactVariableName);
      if (artifact == null) {
        throw new InvalidRequestException(
            format("Artifact corresponding to artifact variable [%s] not found", artifactVariableName), USER);
      }
      artifactStreamAttributes = artifactStreamAttributesMap.get(artifact.getUuid());
      if (artifactStreamAttributes == null) {
        throw new InvalidRequestException(
            format(
                "Artifact Stream Attributes corresponding to artifact variable [%s] not found", artifactVariableName),
            USER);
      }
      metadata = artifactStreamAttributes.getMetadata();
      encryptionDetails = encryptedDataDetailsMap.get(artifact.getUuid());
    } else {
      artifactStreamAttributes = context.getArtifactStreamAttributes();
      encryptionDetails = context.getArtifactServerEncryptedDataDetails();
      metadata = context.getMetadata();
    }
    if (artifactStreamAttributes == null) {
      throw new InvalidRequestException(
          format("Failed to get artifact stream attributes for artifact [%s]", artifactVariableName), USER);
    }
    ArtifactStreamType artifactStreamType =
        ArtifactStreamType.valueOf(artifactStreamAttributes.getArtifactStreamType());
    String command;
    switch (artifactStreamType) {
      case AMAZON_S3:
        command = constructCommandStringForAmazonS3V4(artifactStreamAttributes, encryptionDetails, metadata);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      case ARTIFACTORY:
        command = constructCommandStringForArtifactory(artifactStreamAttributes, encryptionDetails, metadata);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      case SMB:
        command = constructCommandStringForSMB(artifactStreamAttributes, encryptionDetails, metadata);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      case SFTP:
        command = constructCommandStringForSFTP(artifactStreamAttributes, encryptionDetails, metadata);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      case AZURE_ARTIFACTS:
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        List<AzureArtifactsPackageFileInfo> fileInfos = azureArtifactsService.listFiles(
            (AzureArtifactsConfig) artifactStreamAttributes.getServerSetting().getValue(), encryptionDetails,
            artifactStreamAttributes, metadata, true);
        if (isEmpty(fileInfos)) {
          return SUCCESS;
        }

        for (AzureArtifactsPackageFileInfo fileInfo : fileInfos) {
          metadata.put(ArtifactMetadataKeys.artifactFileName, fileInfo.getName());
          command = constructCommandStringForAzureArtifacts(artifactStreamAttributes, encryptionDetails, metadata);
          CommandExecutionStatus executionStatus = context.executeCommandString(command, false);
          if (FAILURE == executionStatus) {
            return executionStatus;
          }
        }
        return SUCCESS;
      case NEXUS:
        command = constructCommandStringForNexus(context, artifactStreamAttributes, encryptionDetails);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      case JENKINS:
        command = constructCommandStringForJenkins(context, artifactStreamAttributes, encryptionDetails);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      case BAMBOO:
        command = constructCommandStringForBamboo(context, artifactStreamAttributes, encryptionDetails);
        log.info("Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        saveExecutionLog(
            context, INFO, "Downloading artifact from " + artifactStreamType.name() + " to " + getCommandPath());
        return context.executeCommandString(command, false);
      default:
        saveExecutionLog(context, ERROR,
            format("Download Artifact not supported for Artifact Stream Type %s", artifactStreamType.name()));
        throw new InvalidRequestException(
            format("Download Artifact not supported for Artifact Stream Type %s", artifactStreamType.name()), USER);
    }
  }

  @Attributes(title = "Command")
  @Override
  public String getCommandString() {
    return null;
  }

  @SchemaIgnore
  @Override
  public String getCommandPath() {
    return super.getCommandPath();
  }

  @SchemaIgnore
  @Override
  public String getPreparedCommand() {
    return super.getPreparedCommand();
  }

  @SchemaIgnore
  @Override
  public List<TailFilePatternEntry> getTailPatterns() {
    return super.getTailPatterns();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("DOWNLOAD_ARTIFACT")
  public static class Yaml extends ExecCommandUnitAbstractYaml {
    private String artifactVariableName;

    public Yaml() {
      super(CommandUnitType.DOWNLOAD_ARTIFACT.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType, String workingDirectory, String scriptType, String command,
        List<TailFilePatternEntry.Yaml> filePatternEntryList, String artifactVariableName) {
      super(name, CommandUnitType.DOWNLOAD_ARTIFACT.name(), deploymentType, workingDirectory, scriptType, command,
          filePatternEntryList);
      this.artifactVariableName = artifactVariableName;
    }
  }

  private void saveExecutionLog(ShellCommandExecutionContext context, LogLevel logLevel, String line) {
    delegateLogService.save(context.getAccountId(),
        aLog()
            .appId(context.getAppId())
            .activityId(context.getActivityId())
            .hostName(context.getHost() == null ? null : context.getHost().getPublicDns())
            .logLevel(logLevel)
            .commandUnitName(getName())
            .logLine(line)
            .executionResult(RUNNING)
            .build());
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
    bucketRegions.put("sa-east-1", "-sa-east-1");
    bucketRegions.put("me-south-1", "-me-south-1");
  }

  private String constructCommandStringForAmazonS3V4(ArtifactStreamAttributes artifactStreamAttributes,
      List<EncryptedDataDetail> encryptionDetails, Map<String, String> metadata) {
    AwsConfig awsConfig = (AwsConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    String awsAccessKey;
    String awsSecretKey;
    String awsToken = null;
    // https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html (Retrieving security
    // credentials from instance metadata)
    if (awsConfig.isUseEc2IamCredentials()) {
      String url = "http://169.254.169.254/";
      AWSTemporaryCredentials credentials = awsHelperService.getCredentialsForIAMROleOnDelegate(url, awsConfig);
      awsAccessKey = credentials.getAccessKeyId();
      awsSecretKey = credentials.getSecretKey();
      awsToken = credentials.getToken();
    } else {
      awsAccessKey = String.valueOf(awsConfig.getAccessKey());
      awsSecretKey = String.valueOf(awsConfig.getSecretKey());
    }
    String bucketName = metadata.get(ArtifactMetadataKeys.bucketName);
    String region = awsHelperService.getBucketRegion(awsConfig, encryptionDetails, bucketName);
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath);
    String artifactFileName = metadata.get(ArtifactMetadataKeys.artifactFileName);
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
    String command;
    switch (this.getScriptType()) {
      // To account for artifacts that have space, we need to encode the artifact portion of the aws url
      case POWERSHELL:
        command = "$Headers = @{\n"
            + "    Authorization = \"" + authorizationHeader + "\"\n"
            + "    \"x-amz-content-sha256\" = \"" + EMPTY_BODY_SHA256 + "\"\n"
            + "    \"x-amz-date\" = \"" + dateTimeStamp + "\"\n"
            + (isEmpty(awsToken) ? "" : " \"x-amz-security-token\" = \"" + awsToken + "\"\n") + "}\n"
            + " $ProgressPreference = 'SilentlyContinue'\n"
            + " Invoke-WebRequest -Uri \""
            + AWS4SignerForAuthorizationHeader.getEndpointWithCanonicalizedResourcePath(endpointUrl, true)
            + "\" -Headers $Headers -OutFile (New-Item -Path \"" + getCommandPath() + "\\" + artifactFileName + "\""
            + " -Force)";
        break;
      case BASH:
        int lastIndexOfSlash = artifactFileName.lastIndexOf('/');
        if (lastIndexOfSlash > 0) {
          artifactFileName = artifactFileName.substring(lastIndexOfSlash + 1);
          log.info("Got filename: " + artifactFileName);
        }
        command = "curl --fail \""
            + AWS4SignerForAuthorizationHeader.getEndpointWithCanonicalizedResourcePath(endpointUrl, true) + "\""
            + " \\\n"
            + "-H \"Authorization: " + authorizationHeader + "\" \\\n"
            + "-H \"x-amz-content-sha256: " + EMPTY_BODY_SHA256 + "\" \\\n"
            + "-H \"x-amz-date: " + dateTimeStamp + "\" \\\n"
            + (isEmpty(awsToken) ? "" : "-H \"x-amz-security-token: " + awsToken + "\" \\\n") + " -o \""
            + getCommandPath() + "/" + artifactFileName + "\"";
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }
    return command;
  }

  private String constructCommandStringForSMB(ArtifactStreamAttributes artifactStreamAttributes,
      List<EncryptedDataDetail> encryptionDetails, Map<String, String> metadata) {
    String artifactFileName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath);
    SmbConfig smbConfig = (SmbConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(smbConfig, encryptionDetails, false);
    String command;
    switch (this.getScriptType()) {
      case POWERSHELL:
        String domain = isNotEmpty(smbConfig.getDomain()) ? smbConfig.getDomain() + "\\" : "";
        String userPassword = "/user:" + domain + smbConfig.getUsername() + " " + new String(smbConfig.getPassword());
        String shareUrl = smbHelperService.getSMBConnectionHost(smbConfig.getSmbUrl()) + "\\"
            + smbHelperService.getSharedFolderName(smbConfig.getSmbUrl());

        // Remove trailing slashes from the folder name as the net use command fails with it.
        String artifactFolder = StringUtils.stripEnd(getArtifactFolder(artifactPath, artifactFileName), "/\\");
        if (!isEmpty(artifactFolder)) {
          // Make all the slashes as back slashes.
          artifactFolder = artifactFolder.replace("/", "\\");
          shareUrl = shareUrl + "\\" + artifactFolder;
        }

        String roboCopyCommand =
            "robocopy \\\\\"" + shareUrl + "\" \"" + getCommandPath() + "\" \"" + artifactFileName + "\"";
        command = "net use \\\\\"" + shareUrl + "\" " + userPassword + " /persistent:no\n " + roboCopyCommand;
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }
    return command;
  }

  private static String getArtifactFolder(String artifactPath, String artifactFileName) {
    return artifactPath.substring(0, artifactPath.lastIndexOf(artifactFileName));
  }

  private String constructCommandStringForSFTP(ArtifactStreamAttributes artifactStreamAttributes,
      List<EncryptedDataDetail> encryptionDetails, Map<String, String> metadata) {
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath);
    SftpConfig sftpConfig = (SftpConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(sftpConfig, encryptionDetails, false);
    String command;
    switch (this.getScriptType()) {
      case POWERSHELL:
        command = "if (Get-Module -ListAvailable -Name Posh-SSH) {\n"
            + "    Write-Host \"Posh-SSH Module exists\"\n"
            + "\n"
            + "} else {\n"
            + "    Write-Host \"Module does not exist, Installing\"\n"
            + "    Install-PackageProvider -Name NuGet -MinimumVersion 2.8.5.201 -Force\n"
            + "    Install-Module -Name Posh-SSH -SkipPublisherCheck -Force\n"
            + "}\n"
            + "\n"
            + "Import-Module Posh-SSH\n"
            + "\n"
            + "$Password = ConvertTo-SecureString "
            + "\'" + new String(sftpConfig.getPassword()) + "\'"
            + " -AsPlainText -Force\n"
            + "$Credential = New-Object System.Management.Automation.PSCredential ('" + sftpConfig.getUsername() + "\'"
            + ", $Password)\n"
            + "\n"
            + "$RemotePath = \"" + artifactPath + "\"\n"
            + "$LocalPath = " + getCommandPath() + "\n"
            + "\n"
            + "$SftpIp = \'" + sftpHelperService.getSFTPConnectionHost(sftpConfig.getSftpUrl()) + "\'\n"
            + "\n"
            + "$ThisSession = New-SFTPSession -ComputerName $SftpIp -Credential $Credential -AcceptKey\n"
            + "\n"
            + "$sftpPathAttribute = Get-SFTPPathAttribute -SessionId ($ThisSession).SessionId -Path  $Remotepath \n"
            + "if ( $sftpPathAttribute.IsRegularFile ) \n"
            + "{ \n"
            + "Get-SFTPFile -SessionId ($ThisSession).SessionId -RemoteFile $Remotepath -LocalPath $LocalPath -Overwrite\n"
            + "} \n\n"
            + "if ( $sftpPathAttribute.IsDirectory ) \n"
            + "{ \n"
            + "$sublist = New-Object System.Collections.Arraylist \n"
            + "Get-SFTPChildItem -SessionId ($ThisSession).SessionId -Path $Remotepath -Recursive | ForEach-Object {  $sublist.add($_.FullName) } \n"
            + "for($i=0; $i -le $sublist.Count; $i++) { \n"
            + "if ( $sublist[$i] ) {\n"
            + "$sftpPathAttribute = Get-SFTPPathAttribute -SessionId ($ThisSession).SessionId -Path  $sublist[$i] \n"
            + "if ( $sftpPathAttribute.IsRegularFile ) {\n"
            + "$FullLocalPath = $LocalPath + \"\\\" + $Remotepath \n"
            + "New-Item -ItemType Directory -Force -Path $FullLocalPath \n"
            + "Get-SFTPFile -SessionId ($ThisSession).SessionId -RemoteFile $sublist[$i] -LocalPath $FullLocalPath -Overwrite \n"
            + "} \n"
            + "} \n"
            + "} \n"
            + "} \n\n"
            + "\n"
            + "Get-SFTPSession | % { Remove-SFTPSession -SessionId ($_.SessionId) }";
        break;

      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }
    return command;
  }

  private String constructCommandStringForArtifactory(ArtifactStreamAttributes artifactStreamAttributes,
      List<EncryptedDataDetail> encryptionDetails, Map<String, String> metadata) {
    String artifactFileName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    int lastIndexOfSlash = artifactFileName.lastIndexOf('/');
    if (lastIndexOfSlash > 0) {
      artifactFileName = artifactFileName.substring(lastIndexOfSlash + 1);
      log.info("Got filename: " + artifactFileName);
    }
    ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(artifactoryConfig, encryptionDetails, false);
    String authHeader = null;
    if (artifactoryConfig.hasCredentials()) {
      String pair = artifactoryConfig.getUsername() + ":" + new String(artifactoryConfig.getPassword());
      authHeader = "Basic " + encodeBase64(pair);
    }
    String command;
    switch (this.getScriptType()) {
      case BASH:
        if (!artifactoryConfig.hasCredentials()) {
          command = "curl -L --fail -X GET \""
              + getArtifactoryUrl(artifactoryConfig, metadata.get(ArtifactMetadataKeys.artifactPath)) + "\" -o \""
              + getCommandPath() + "/" + artifactFileName + "\"";
        } else {
          command = "curl -L --fail -H \"Authorization: " + authHeader + "\" -X GET \""
              + getArtifactoryUrl(artifactoryConfig, metadata.get(ArtifactMetadataKeys.artifactPath)) + "\" -o \""
              + getCommandPath() + "/" + artifactFileName + "\"";
        }
        break;
      case POWERSHELL:
        if (!artifactoryConfig.hasCredentials()) {
          command = "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n "
              + "$ProgressPreference = 'SilentlyContinue'\n"
              + "Invoke-WebRequest -Uri \""
              + getArtifactoryUrl(artifactoryConfig, metadata.get(ArtifactMetadataKeys.artifactPath)) + "\" -OutFile \""
              + getCommandPath() + "\\" + artifactFileName + "\"";
        } else {
          command = "$Headers = @{\n"
              + "    Authorization = \"" + authHeader + "\"\n"
              + "}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12"
              + "\n $ProgressPreference = 'SilentlyContinue'"
              + "\n Invoke-WebRequest -Uri \""
              + getArtifactoryUrl(artifactoryConfig, metadata.get(ArtifactMetadataKeys.artifactPath))
              + "\" -Headers $Headers -OutFile \"" + getCommandPath() + "\\" + artifactFileName + "\"";
        }
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }
    return command;
  }

  private String constructCommandStringForNexus(ShellCommandExecutionContext context,
      ArtifactStreamAttributes artifactStreamAttributes, List<EncryptedDataDetail> encryptionDetails) {
    NexusRequest nexusRequest = NexusConfigToNexusRequestMapper.toNexusRequest(
        (NexusConfig) artifactStreamAttributes.getServerSetting().getValue(), encryptionService, encryptionDetails);
    String authHeader = null;
    if (nexusRequest.isHasCredentials()) {
      String pair = nexusRequest.getUsername() + ":" + new String(nexusRequest.getPassword());
      authHeader = "Basic " + encodeBase64(pair);
    }
    List<ArtifactFileMetadata> artifactFileMetadata = artifactStreamAttributes.getArtifactFileMetadata();
    StringBuilder command = new StringBuilder(128);

    if (isEmpty(artifactFileMetadata)) {
      // Try once more of to get download url
      try {
        List<BuildDetails> buildDetailsList;
        if (artifactStreamAttributes.getRepositoryType() != null
            && artifactStreamAttributes.getRepositoryType().equals(RepositoryType.maven.name())) {
          buildDetailsList = nexusTwoService.getVersion(nexusRequest, artifactStreamAttributes.getRepositoryName(),
              artifactStreamAttributes.getGroupId(), artifactStreamAttributes.getArtifactName(),
              artifactStreamAttributes.getExtension(), artifactStreamAttributes.getClassifier(),
              artifactStreamAttributes.getMetadata().get("buildNo"));

        } else {
          buildDetailsList = Collections.singletonList(
              nexusTwoService.getVersion(artifactStreamAttributes.getRepositoryFormat(), nexusRequest,
                  artifactStreamAttributes.getRepositoryName(), artifactStreamAttributes.getNexusPackageName(),
                  artifactStreamAttributes.getMetadata().get("buildNo")));
        }

        if (isEmpty(buildDetailsList) || isEmpty(buildDetailsList.get(0).getArtifactFileMetadataList())) {
          saveExecutionLog(context, ERROR, NO_ARTIFACTS_ERROR_STRING);
          throw new InvalidRequestException(NO_ARTIFACTS_ERROR_STRING, USER);
        } else {
          artifactFileMetadata = buildDetailsList.get(0).getArtifactFileMetadataList();
          log.info("Found metadata for artifact: {}", buildDetailsList.get(0).getUiDisplayName());
        }
      } catch (IOException ioException) {
        log.warn("Exception encountered while fetching download url for artifact", ioException);
      }
    }

    // filter artifacts based on extension and classifier for nexus parameterized artifact stream.
    // No op for non-parameterized artifact stream because we have already filtered artifactFileMetadata before we reach
    // here
    if (isNotEmpty(artifactStreamAttributes.getExtension())) {
      artifactFileMetadata =
          artifactFileMetadata.stream()
              .filter(aFileMetadata
                  -> aFileMetadata.getFileName().endsWith(PERIOD_DELIMITER + artifactStreamAttributes.getExtension()))
              .collect(Collectors.toList());
    }

    if (isNotEmpty(artifactStreamAttributes.getClassifier())) {
      artifactFileMetadata =
          artifactFileMetadata.stream()
              .filter(aFileMetadata -> aFileMetadata.getFileName().contains(artifactStreamAttributes.getClassifier()))
              .collect(Collectors.toList());
    }

    switch (this.getScriptType()) {
      case BASH:
        for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
          if (!nexusRequest.isHasCredentials()) {
            command.append("curl --fail -X GET \"")
                .append(downloadMetadata.getUrl())
                .append("\" -o \"")
                .append(getCommandPath().trim())
                .append('/')
                .append(downloadMetadata.getFileName())
                .append("\"\n");
          } else {
            command.append("curl --fail -H \"Authorization: ")
                .append(authHeader)
                .append("\" -X GET \"")
                .append(downloadMetadata.getUrl())
                .append("\" -o \"")
                .append(getCommandPath().trim())
                .append('/')
                .append(downloadMetadata.getFileName())
                .append("\"\n");
          }
        }
        break;
      case POWERSHELL:
        if (nexusRequest.isHasCredentials()) {
          command
              .append("$Headers = @{\n"
                  + "    Authorization = \"")
              .append(authHeader)
              .append(
                  "\"\n}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n $ProgressPreference = 'SilentlyContinue'");
          for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
            command.append("\n Invoke-WebRequest -Uri \"")
                .append(downloadMetadata.getUrl())
                .append("\" -Headers $Headers -OutFile \"")
                .append(getCommandPath().trim())
                .append('\\')
                .append(downloadMetadata.getFileName())
                .append('"');
          }
        } else {
          command.append(
              "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n $ProgressPreference = 'SilentlyContinue'");
          for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
            command.append("\n Invoke-WebRequest -Uri \"")
                .append(downloadMetadata.getUrl())
                .append("\" -OutFile \"")
                .append(getCommandPath().trim())
                .append('\\')
                .append(downloadMetadata.getFileName())
                .append('"');
          }
        }
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }

    return command.toString();
  }

  private String constructCommandStringForJenkins(ShellCommandExecutionContext context,
      ArtifactStreamAttributes artifactStreamAttributes, List<EncryptedDataDetail> encryptionDetails) {
    JenkinsConfig jenkinsConfig = (JenkinsConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
    String pair = jenkinsConfig.getUsername() + ":" + new String(jenkinsConfig.getPassword());
    String authHeader = "Basic " + encodeBase64(pair);

    List<ArtifactFileMetadata> artifactFileMetadata = artifactStreamAttributes.getArtifactFileMetadata();
    StringBuilder command = new StringBuilder(128);

    if (isEmpty(artifactFileMetadata)) {
      saveExecutionLog(context, ERROR, NO_ARTIFACTS_ERROR_STRING);
      throw new InvalidRequestException(NO_ARTIFACTS_ERROR_STRING, USER);
    }

    switch (this.getScriptType()) {
      case BASH:
        for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
          command.append("curl --fail -H \"Authorization: ")
              .append(authHeader)
              .append("\" -X GET \"")
              .append(downloadMetadata.getUrl())
              .append("\" -o \"")
              .append(getCommandPath().trim())
              .append('/')
              .append(downloadMetadata.getFileName())
              .append("\"\n");
        }
        break;
      case POWERSHELL:
        command
            .append("$webClient = New-Object System.Net.WebClient \n"
                + "$webClient.Headers[[System.Net.HttpRequestHeader]::Authorization] = \"")
            .append(authHeader)
            .append("\";\n");
        for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
          command.append("$url = \"")
              .append(downloadMetadata.getUrl())
              .append("\" \n$localfilename = \"")
              .append(getCommandPath().trim())
              .append('\\')
              .append(downloadMetadata.getFileName())
              .append("\" \n$webClient.DownloadFile($url, $localfilename) \n");
        }
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }

    return command.toString();
  }

  private String constructCommandStringForBamboo(ShellCommandExecutionContext context,
      ArtifactStreamAttributes artifactStreamAttributes, List<EncryptedDataDetail> encryptionDetails) {
    BambooConfig bambooConfig = (BambooConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(bambooConfig, encryptionDetails, false);
    String pair = bambooConfig.getUsername() + ":" + new String(bambooConfig.getPassword());
    String authHeader = "Basic " + encodeBase64(pair);

    List<ArtifactFileMetadata> artifactFileMetadata = artifactStreamAttributes.getArtifactFileMetadata();
    StringBuilder command = new StringBuilder(128);

    if (isEmpty(artifactFileMetadata)) {
      saveExecutionLog(context, ERROR, NO_ARTIFACTS_ERROR_STRING);
      throw new InvalidRequestException(NO_ARTIFACTS_ERROR_STRING, USER);
    }

    switch (this.getScriptType()) {
      case BASH:
        for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
          command.append("curl --fail -H \"Authorization: ")
              .append(authHeader)
              .append("\" -X GET \"")
              .append(downloadMetadata.getUrl())
              .append("\" -o \"")
              .append(getCommandPath().trim())
              .append('/')
              .append(downloadMetadata.getFileName())
              .append("\"\n");
        }
        break;
      case POWERSHELL:
        command
            .append("$Headers = @{\n"
                + "    Authorization = \"")
            .append(authHeader)
            .append(
                "\"\n}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n $ProgressPreference = 'SilentlyContinue'");
        for (ArtifactFileMetadata downloadMetadata : artifactFileMetadata) {
          command.append("\n Invoke-WebRequest -Uri \"")
              .append(downloadMetadata.getUrl())
              .append("\" -Headers $Headers -OutFile \"")
              .append(getCommandPath().trim())
              .append('\\')
              .append(downloadMetadata.getFileName())
              .append('"');
        }
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }

    return command.toString();
  }

  private String getArtifactoryUrl(ArtifactoryConfig config, String artifactPath) {
    String url = config.fetchRegistryUrl().trim();
    if (!url.endsWith("/")) {
      url += "/";
    }
    return url + artifactPath;
  }

  private String constructCommandStringForAzureArtifacts(ArtifactStreamAttributes artifactStreamAttributes,
      List<EncryptedDataDetail> encryptionDetails, Map<String, String> metadata) {
    String artifactFileName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    int lastIndexOfSlash = artifactFileName.lastIndexOf('/');
    if (lastIndexOfSlash > 0) {
      artifactFileName = artifactFileName.substring(lastIndexOfSlash + 1);
      log.info("Got filename: " + artifactFileName);
    }

    AzureArtifactsConfig azureArtifactsConfig =
        (AzureArtifactsConfig) artifactStreamAttributes.getServerSetting().getValue();
    encryptionService.decrypt(azureArtifactsConfig, encryptionDetails, false);
    String authHeader = AzureArtifactsServiceHelper.getAuthHeader(azureArtifactsConfig);
    String version = metadata.getOrDefault(ArtifactMetadataKeys.version, null);
    if (isBlank(version)) {
      throw new InvalidRequestException("Invalid version", USER);
    }

    String url = AzureArtifactsServiceHelper.getDownloadUrl(
        azureArtifactsConfig.getAzureDevopsUrl(), artifactStreamAttributes, version, artifactFileName);
    if (isBlank(url)) {
      throw new InvalidRequestException("Unable to generate download URL", USER);
    }

    String command;
    switch (this.getScriptType()) {
      case BASH:
        command = "curl --fail -L -H \"Authorization: " + authHeader + "\" -X GET \"" + url + "\" -o \""
            + getCommandPath() + "/" + artifactFileName + "\"";
        break;
      case POWERSHELL:
        command = "$Headers = @{\n"
            + "    Authorization = \"" + authHeader + "\"\n"
            + "}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12"
            + "\n $ProgressPreference = 'SilentlyContinue'"
            + "\n Invoke-WebRequest -Uri \"" + url + "\" -Headers $Headers -OutFile \"" + getCommandPath() + "\\"
            + artifactFileName + "\"";
        break;
      default:
        throw new InvalidRequestException("Invalid Script type", USER);
    }
    return command;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String commandPath;
    private ScriptType scriptType;
    private String name;
    private CommandUnitType commandUnitType;
    private String artifactVariableName;

    private Builder() {}

    /**
     * A DownloadArtifact command unit builder.
     *
     * @return the builder
     */
    public static Builder aDownloadArtifactCommandUnit() {
      return new Builder();
    }

    /**
     * With file category builder.
     *
     * @param commandPath the default command directory
     * @return the builder
     */
    public Builder withCommandPath(String commandPath) {
      this.commandPath = commandPath;
      return this;
    }

    /**
     * With destination directory path builder.
     *
     * @param scriptType the script type
     * @return the builder
     */
    public Builder withScriptType(ScriptType scriptType) {
      this.scriptType = scriptType;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With command unit type builder.
     *
     * @param commandUnitType the command unit type
     * @return the builder
     */
    public Builder withCommandUnitType(CommandUnitType commandUnitType) {
      this.commandUnitType = commandUnitType;
      return this;
    }

    /**
     * With command unit type builder.
     *
     * @param artifactVariableName the artifact variable name
     * @return the builder
     */
    public Builder withArtifactVariableName(String artifactVariableName) {
      this.artifactVariableName = artifactVariableName;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aDownloadArtifactCommandUnit()
          .withCommandPath(commandPath)
          .withScriptType(scriptType)
          .withName(name)
          .withCommandUnitType(commandUnitType)
          .withArtifactVariableName(artifactVariableName);
    }

    /**
     * Build download artifact command unit.
     *
     * @return the download artifact command unit
     */
    public DownloadArtifactCommandUnit build() {
      DownloadArtifactCommandUnit downloadArtifactCommandUnit = new DownloadArtifactCommandUnit();
      downloadArtifactCommandUnit.setScriptType(scriptType);
      downloadArtifactCommandUnit.setCommandPath(commandPath);
      downloadArtifactCommandUnit.setName(name);
      downloadArtifactCommandUnit.setCommandUnitType(commandUnitType);
      downloadArtifactCommandUnit.setArtifactVariableName(artifactVariableName);
      return downloadArtifactCommandUnit;
    }
  }
}
