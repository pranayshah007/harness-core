/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.checkIfFileExist;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.pcf.model.PcfConstants.APPLICATION_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.BIN_BASH;
import static io.harness.pcf.model.PcfConstants.CREATE_SERVICE_MANIFEST_ELEMENT;
import static io.harness.pcf.model.PcfConstants.DOCKER_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ENV_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.HARNESS__STAGE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STATUS__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.IMAGE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.INSTANCE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.NO_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PATH_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PROCESSES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PROCESSES_TYPE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.RANDOM_ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTES_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ROUTE_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.USERNAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.WEB_PROCESS_TYPE_MANIFEST_YML_ELEMENT;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FileCreationException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfCreateApplicationRequestData;

import software.wings.beans.AwsConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 * Stateles helper class
 */
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class PcfCommandTaskHelper {
  private static final Yaml yaml;
  public static final String PROCESSED_ARTIFACT_DIRECTORY = "\\$\\{processedArtifactDir}";
  public static final String DOWNLOADED_ARTIFACT_PLACEHOLDER = "\\$\\{downloadedArtifact}";

  static {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(FlowStyle.BLOCK);
    options.setExplicitStart(true);
    yaml = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()), options);
  }

  public static final String DELIMITER = "__";

  @Inject private DelegateFileManager delegateFileManager;
  @Inject private PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Inject private CfDeploymentManager pcfDeploymentManager;

  @VisibleForTesting
  public File downloadArtifact(CfCommandSetupRequest cfCommandSetupRequest, File workingDirectory,
      LogCallback executionLogCallback) throws IOException, ExecutionException {
    InputStream artifactFileStream = delegateFileManager.downloadArtifactAtRuntime(
        cfCommandSetupRequest.getArtifactStreamAttributes(), cfCommandSetupRequest.getAccountId(),
        cfCommandSetupRequest.getAppId(), cfCommandSetupRequest.getActivityId(), cfCommandSetupRequest.getCommandName(),
        cfCommandSetupRequest.getArtifactStreamAttributes().getRegistryHostName());
    String fileName =
        System.currentTimeMillis() + cfCommandSetupRequest.getArtifactStreamAttributes().getArtifactName();

    if (isNotEmpty(cfCommandSetupRequest.getArtifactProcessingScript())) {
      return processArtifact(cfCommandSetupRequest, workingDirectory, executionLogCallback, artifactFileStream,
          FilenameUtils.getName(fileName));
    }

    File artifactFile = new File(workingDirectory.getAbsolutePath() + PATH_DELIMITER + FilenameUtils.getName(fileName));

    if (!artifactFile.createNewFile()) {
      throw new FileCreationException("Failed to create file " + artifactFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }
    IOUtils.copy(artifactFileStream, new FileOutputStream(artifactFile));

    return artifactFile;
  }

  private File processArtifact(CfCommandSetupRequest cfCommandSetupRequest, File workingDirectory,
      LogCallback executionLogCallback, InputStream artifactFileStream, String fileName) throws IOException {
    String tempWorkingDirectoryPath = generateFilepath(workingDirectory.getAbsolutePath());
    createDirectoryIfDoesNotExist(tempWorkingDirectoryPath);
    File tempWorkingDirectory = new File(tempWorkingDirectoryPath);

    String finalArtifactTempDirectoryPath = generateFilepath(tempWorkingDirectoryPath);
    createDirectoryIfDoesNotExist(finalArtifactTempDirectoryPath);

    File downloadedArtifactFile = new File(tempWorkingDirectoryPath + PATH_DELIMITER + fileName);

    if (!downloadedArtifactFile.createNewFile()) {
      throw new FileCreationException("Failed to create file " + downloadedArtifactFile.getCanonicalPath(), null,
          ErrorCode.FILE_CREATE_ERROR, Level.ERROR, USER, null);
    }

    IOUtils.copy(artifactFileStream, new FileOutputStream(downloadedArtifactFile));
    replaceScriptPlaceholders(cfCommandSetupRequest, finalArtifactTempDirectoryPath, downloadedArtifactFile);

    try {
      executeArtifactProcessingScript(cfCommandSetupRequest, executionLogCallback, tempWorkingDirectory);
    } catch (Exception e) {
      throw new InvalidArgumentsException("Failed to execute artifact processing script");
    }

    File[] files = new File(finalArtifactTempDirectoryPath).listFiles();

    if (files != null && files.length > 0 && files[0].exists()) {
      File tempArtifactFile = files[0];

      File artifactFile;

      if (tempArtifactFile.isDirectory()) {
        FileUtils.moveToDirectory(tempArtifactFile, workingDirectory, false);
        artifactFile = FileUtils.getFile(workingDirectory, tempArtifactFile.getName());
      } else {
        artifactFile = new File(
            workingDirectory.getAbsolutePath() + PATH_DELIMITER + FilenameUtils.getName(tempArtifactFile.getName()));
        FileUtils.moveFile(tempArtifactFile, artifactFile);
      }

      deleteDirectoryAndItsContentIfExists(tempWorkingDirectory.getAbsolutePath());
      return artifactFile;
    } else {
      throw new InvalidArgumentsException(
          String.format("Final artifact was not copied to %s", PROCESSED_ARTIFACT_DIRECTORY));
    }
  }

  private String generateFilepath(String path) throws IOException {
    String generatedPath = RandomStringUtils.randomAlphanumeric(5);
    while (checkIfFileExist(path + PATH_DELIMITER + generatedPath)) {
      generatedPath = RandomStringUtils.randomAlphanumeric(5);
    }
    return path + PATH_DELIMITER + generatedPath;
  }

  private void replaceScriptPlaceholders(
      CfCommandSetupRequest cfCommandSetupRequest, String finalArtifactTempDirectoryPath, File downloadedArtifactFile) {
    cfCommandSetupRequest.setArtifactProcessingScript(cfCommandSetupRequest.getArtifactProcessingScript().replaceAll(
        DOWNLOADED_ARTIFACT_PLACEHOLDER, downloadedArtifactFile.getAbsolutePath()));
    cfCommandSetupRequest.setArtifactProcessingScript(cfCommandSetupRequest.getArtifactProcessingScript().replaceAll(
        PROCESSED_ARTIFACT_DIRECTORY, finalArtifactTempDirectoryPath));
  }

  private void executeArtifactProcessingScript(CfCommandSetupRequest cfCommandSetupRequest,
      LogCallback executionLogCallback, File directory) throws IOException, TimeoutException, InterruptedException {
    executionLogCallback.saveExecutionLog(color("# Executing artifact processing script: ", White, Bold));

    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(cfCommandSetupRequest.getTimeoutIntervalInMin(), TimeUnit.MINUTES)
                                          .command(BIN_BASH, "-c", cfCommandSetupRequest.getArtifactProcessingScript())
                                          .directory(directory)
                                          .readOutput(true)
                                          .redirectOutput(new LogOutputStream() {
                                            @Override
                                            protected void processLine(String line) {
                                              executionLogCallback.saveExecutionLog(line);
                                            }
                                          });
    ProcessResult processResult = processExecutor.execute();

    int exitCode = processResult.getExitValue();
    if (exitCode == 0) {
      executionLogCallback.saveExecutionLog(format(String.valueOf(SUCCESS), Bold, Green));
    } else {
      executionLogCallback.saveExecutionLog(format(processResult.outputUTF8(), Bold, Red), ERROR);
    }
  }

  public File downloadArtifactFromManager(LogCallback executionLogCallback, CfCommandSetupRequest cfCommandSetupRequest,
      File workingDirectory) throws IOException, ExecutionException {
    List<ArtifactFile> artifactFiles = cfCommandSetupRequest.getArtifactFiles();
    String accountId = cfCommandSetupRequest.getAccountId();
    List<Pair<String, String>> fileIds = Lists.newArrayList();

    if (isEmpty(cfCommandSetupRequest.getArtifactFiles())) {
      throw new InvalidArgumentsException(Pair.of("Artifact", "is not available"));
    }

    artifactFiles.forEach(artifactFile -> fileIds.add(Pair.of(artifactFile.getFileUuid(), null)));
    try (InputStream inputStream =
             delegateFileManager.downloadArtifactByFileId(FileBucket.ARTIFACTS, fileIds.get(0).getKey(), accountId)) {
      String fileName = System.currentTimeMillis() + artifactFiles.get(0).getName();

      if (isNotEmpty(cfCommandSetupRequest.getArtifactProcessingScript())) {
        return processArtifact(cfCommandSetupRequest, workingDirectory, executionLogCallback, inputStream, fileName);
      }

      File artifactFile = new File(workingDirectory.getAbsolutePath() + PATH_DELIMITER + fileName);

      if (!artifactFile.createNewFile()) {
        throw new WingsException(ErrorCode.GENERAL_ERROR)
            .addParam("message", "Failed to create file " + artifactFile.getCanonicalPath());
      }
      IOUtils.copy(inputStream, new FileOutputStream(artifactFile));
      return artifactFile;
    }
  }

  public String generateManifestYamlForPush(CfCommandSetupRequest cfCommandSetupRequest,
      CfCreateApplicationRequestData requestData) throws PivotalClientApiException {
    // Substitute name,
    String manifestYaml = cfCommandSetupRequest.getManifestYaml();

    Map<String, Object> map;
    try {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      map = (Map<String, Object>) mapper.readValue(manifestYaml, Map.class);
    } catch (Exception e) {
      throw new UnexpectedException("Failed to get Yaml Map", e);
    }

    List<Map> applicationMaps = (List<Map>) map.get(APPLICATION_YML_ELEMENT);

    if (isEmpty(applicationMaps)) {
      throw new InvalidArgumentsException(
          Pair.of("Manifest.yml does not have any elements under \'applications\'", manifestYaml));
    }

    // We always assume, first app is main application being deployed.
    Map mapForUpdate = applicationMaps.get(0);

    // Use TreeMap that uses case insensitive keys
    TreeMap<String, Object> applicationToBeUpdated = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    applicationToBeUpdated.putAll(mapForUpdate);

    // Update Name only if vars file is not present, legacy
    applicationToBeUpdated.put(NAME_MANIFEST_YML_ELEMENT, requestData.getNewReleaseName());
    updateArtifactDetails(requestData, cfCommandSetupRequest, applicationToBeUpdated);
    applicationToBeUpdated.put(INSTANCE_MANIFEST_YML_ELEMENT, 0);

    if (applicationToBeUpdated.containsKey(PROCESSES_MANIFEST_YML_ELEMENT)) {
      Object processes = applicationToBeUpdated.get(PROCESSES_MANIFEST_YML_ELEMENT);
      if (processes instanceof ArrayList<?>) {
        ArrayList<Map<String, Object>> allProcesses = (ArrayList<Map<String, Object>>) processes;
        for (Map<String, Object> process : allProcesses) {
          Object p = process.get(PROCESSES_TYPE_MANIFEST_YML_ELEMENT);
          if ((p instanceof String) && (p.toString().equals(WEB_PROCESS_TYPE_MANIFEST_YML_ELEMENT))) {
            process.put(INSTANCE_MANIFEST_YML_ELEMENT, 0);
          }
        }
      }
    }
    // Update routes.
    updateConfigWithRoutesIfRequired(requestData, applicationToBeUpdated, cfCommandSetupRequest);
    // We do not want to change order

    // remove "create-services" elements as it would have been used by cf cli plugin to create services.
    // This elements is not needed for cf push
    map.remove(CREATE_SERVICE_MANIFEST_ELEMENT);
    addInactiveIdentifierToManifest(applicationToBeUpdated, requestData, cfCommandSetupRequest);
    Map<String, Object> applicationMapForYamlDump =
        pcfCommandTaskBaseHelper.generateFinalMapForYamlDump(applicationToBeUpdated);

    // replace map for first application that we are deploying
    applicationMaps.set(0, applicationMapForYamlDump);
    try {
      return yaml.dump(map);
    } catch (Exception e) {
      throw new PivotalClientApiException(new StringBuilder()
                                              .append("Failed to generate final version of  Manifest.yml file. ")
                                              .append(manifestYaml)
                                              .toString(),
          e);
    }
  }

  void updateArtifactDetails(CfCreateApplicationRequestData requestData, CfCommandSetupRequest cfCommandSetupRequest,
      TreeMap<String, Object> applicationToBeUpdated) {
    if (!cfCommandSetupRequest.getArtifactStreamAttributes().isDockerBasedDeployment()) {
      applicationToBeUpdated.put(PATH_MANIFEST_YML_ELEMENT, requestData.getArtifactPath());
    } else {
      Map<String, Object> dockerDetails = new HashMap<>();
      ArtifactStreamAttributes artifactStreamAttributes = cfCommandSetupRequest.getArtifactStreamAttributes();
      String dockerImagePath = artifactStreamAttributes.getMetadata().get(IMAGE_MANIFEST_YML_ELEMENT);
      String username = getUsername(cfCommandSetupRequest);
      dockerDetails.put(IMAGE_MANIFEST_YML_ELEMENT, dockerImagePath);
      if (!isEmpty(username)) {
        dockerDetails.put(USERNAME_MANIFEST_YML_ELEMENT, username);
      }
      applicationToBeUpdated.put(DOCKER_MANIFEST_YML_ELEMENT, dockerDetails);
    }
  }

  private String getUsername(CfCommandSetupRequest cfCommandSetupRequest) {
    String username = "";
    SettingAttribute serverSetting = cfCommandSetupRequest.getArtifactStreamAttributes().getServerSetting();
    if (serverSetting.getValue() instanceof DockerConfig) {
      DockerConfig dockerConfig = (DockerConfig) serverSetting.getValue();
      username = isEmpty(dockerConfig.getPassword()) ? EMPTY : dockerConfig.getUsername();
    } else if (serverSetting.getValue() instanceof AwsConfig) {
      AwsConfig awsConfig = (AwsConfig) serverSetting.getValue();
      username = isEmpty(awsConfig.getSecretKey()) ? EMPTY : String.valueOf(awsConfig.getAccessKey());
    } else if (serverSetting.getValue() instanceof ArtifactoryConfig) {
      ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) serverSetting.getValue();
      username = isEmpty(artifactoryConfig.getPassword()) ? EMPTY : artifactoryConfig.getUsername();
    } else if (serverSetting.getValue() instanceof GcpConfig) {
      GcpConfig gcpConfig = (GcpConfig) serverSetting.getValue();
      username = isEmpty(gcpConfig.getServiceAccountKeyFileContent()) ? EMPTY : "_json_key";
    } else if (serverSetting.getValue() instanceof NexusConfig) {
      NexusConfig nexusConfig = (NexusConfig) serverSetting.getValue();
      username = isEmpty(nexusConfig.getPassword()) ? EMPTY : nexusConfig.getUsername();
    }
    return username;
  }

  // Add Env Variable marking this deployment version as Inactive
  private void addInactiveIdentifierToManifest(
      Map<String, Object> map, CfCreateApplicationRequestData requestData, CfCommandSetupRequest setupRequest) {
    if (!setupRequest.isBlueGreen()) {
      return;
    }

    Map<String, Object> envMap = null;
    if (map.containsKey(ENV_MANIFEST_YML_ELEMENT)) {
      envMap = (Map<String, Object>) map.get(ENV_MANIFEST_YML_ELEMENT);
    }

    if (envMap == null) {
      envMap = new HashMap<>();
    }

    envMap.put(HARNESS__STATUS__IDENTIFIER, HARNESS__STAGE__IDENTIFIER);
    map.put(ENV_MANIFEST_YML_ELEMENT, envMap);
  }

  private void updateConfigWithRoutesIfRequired(
      CfCreateApplicationRequestData requestData, TreeMap applicationToBeUpdated, CfCommandSetupRequest setupRequest) {
    applicationToBeUpdated.remove(ROUTES_MANIFEST_YML_ELEMENT);

    // 1. Check and handle no-route scenario
    boolean isNoRoute = applicationToBeUpdated.containsKey(NO_ROUTE_MANIFEST_YML_ELEMENT)
        && (boolean) applicationToBeUpdated.get(NO_ROUTE_MANIFEST_YML_ELEMENT);
    if (isNoRoute) {
      pcfCommandTaskBaseHelper.handleManifestWithNoRoute(applicationToBeUpdated, setupRequest.isBlueGreen());
      return;
    }

    // 2. Check if random-route config is needed. This happens if random-route=true in manifest or
    // user has not provided any route value.
    if (pcfCommandTaskBaseHelper.shouldUseRandomRoute(applicationToBeUpdated, setupRequest.getRouteMaps())) {
      applicationToBeUpdated.put(RANDOM_ROUTE_MANIFEST_YML_ELEMENT, true);
      return;
    }

    // 3. Insert routes provided by user.
    List<String> routesForUse = setupRequest.getRouteMaps();
    List<Map<String, String>> routeMapList = new ArrayList<>();
    routesForUse.forEach(routeString -> {
      Map<String, String> mapEntry = Collections.singletonMap(ROUTE_MANIFEST_YML_ELEMENT, routeString);
      routeMapList.add(mapEntry);
    });

    // Add this route config to applicationConfig
    applicationToBeUpdated.put(ROUTES_MANIFEST_YML_ELEMENT, routeMapList);
  }

  public char[] getPassword(ArtifactStreamAttributes artifactStreamAttributes) {
    char[] password = null;
    SettingAttribute serverSetting = artifactStreamAttributes.getServerSetting();
    if (serverSetting.getValue() instanceof DockerConfig) {
      DockerConfig dockerConfig = (DockerConfig) serverSetting.getValue();
      password = dockerConfig.getPassword();
    } else if (serverSetting.getValue() instanceof AwsConfig) {
      AwsConfig awsConfig = (AwsConfig) serverSetting.getValue();
      password = awsConfig.getSecretKey();
    } else if (serverSetting.getValue() instanceof ArtifactoryConfig) {
      ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) serverSetting.getValue();
      password = artifactoryConfig.getPassword();
    } else if (serverSetting.getValue() instanceof GcpConfig) {
      GcpConfig gcpConfig = (GcpConfig) serverSetting.getValue();
      String serviceAccountKeyFileContent = new String(gcpConfig.getServiceAccountKeyFileContent());
      password = serviceAccountKeyFileContent.replaceAll("\n", "").toCharArray();
    } else if (serverSetting.getValue() instanceof NexusConfig) {
      NexusConfig nexusConfig = (NexusConfig) serverSetting.getValue();
      password = nexusConfig.getPassword();
    }
    return password;
  }
}
