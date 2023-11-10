/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifactBundle.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.helm.CustomManifestFetchTaskHelper.unzipManifestFiles;
import static io.harness.delegate.task.pcf.artifact.TasArtifactBundledArtifactType.ZIP;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.getFilesUnderPath;
import static io.harness.pcf.model.ManifestType.APPLICATION_MANIFEST;
import static io.harness.pcf.model.ManifestType.AUTOSCALAR_MANIFEST;
import static io.harness.pcf.model.ManifestType.VARIABLE_MANIFEST;
import static io.harness.pcf.model.PcfConstants.APPLICATION_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.ARTIFACT_BUNDLE_MANIFESTS;
import static io.harness.pcf.model.PcfConstants.NAME_MANIFEST_YML_ELEMENT;
import static io.harness.pcf.model.PcfConstants.PCF_AUTOSCALAR_MANIFEST_INSTANCE_LIMITS_ELE;
import static io.harness.pcf.model.PcfConstants.PCF_AUTOSCALAR_MANIFEST_RULES_ELE;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.counting;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FileData;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.artifactBundle.ArtifactBundleConfig;
import io.harness.delegate.task.artifactBundle.TasArtifactBundleConfig;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadContext;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasArtifactType;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.manifest.TasManifestDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.UnexpectedException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.pcf.model.ManifestType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Slf4j
@OwnedBy(CDP)
public class TasArtifactBundleFetchService implements ArtifactBundleFetchService {
  @Inject protected CfCommandTaskHelperNG cfCommandTaskHelperNG;
  @Inject PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;

  @Override
  public File downloadArtifactFile(ArtifactBundleConfig artifactBundleConfig, LogCallback logCallback)
      throws IOException {
    File artifactFile = null;
    TasArtifactBundleConfig tasArtifactBundleConfig = (TasArtifactBundleConfig) artifactBundleConfig;
    File workingDirectory = pcfCommandTaskBaseHelper.generateWorkingDirectoryForDeployment();
    if (isPackageArtifact(tasArtifactBundleConfig.getTasArtifactConfig())) {
      TasArtifactDownloadResponse tasArtifactDownloadResponse = cfCommandTaskHelperNG.downloadPackageArtifact(
          TasArtifactDownloadContext.builder()
              .artifactConfig((TasPackageArtifactConfig) tasArtifactBundleConfig.getTasArtifactConfig())
              .workingDirectory(workingDirectory)
              .build(),
          logCallback);
      artifactFile = tasArtifactDownloadResponse.getArtifactFile();
    }
    return artifactFile;
  }

  @Override
  public Map<String, List<FileData>> getManifestFilesFromArtifactBundle(
      File artifactBundleFile, ArtifactBundleConfig artifactBundleConfig, String activityId, LogCallback logCallback) {
    try {
      Map<String, List<FileData>> manifestsFromArtifactBundle = new HashMap<>();
      File extractedFile;
      TasManifestDelegateConfig tasManifestDelegateConfig =
          ((TasArtifactBundleConfig) artifactBundleConfig).getTasManifestDelegateConfig();
      if (tasManifestDelegateConfig.getArtifactBundleType().equals(ZIP)) {
        extractedFile = upzipFile(artifactBundleFile, activityId);
      }
      File file = upzipFile(artifactBundleFile, activityId);
      if (tasManifestDelegateConfig.getManifestPath() != null) {
        List<FileData> manifestFiles = readManifestFilesFromDirectory(
            Paths.get(file.getAbsolutePath(), tasManifestDelegateConfig.getManifestPath()).normalize().toString(),
            logCallback);
        manifestsFromArtifactBundle.put(tasManifestDelegateConfig.getIdentifier(), manifestFiles);
      }
      if (tasManifestDelegateConfig.getVarsPaths() != null) {
        List<FileData> varFilePathList = new ArrayList<>();
        for (String varPath : tasManifestDelegateConfig.getVarsPaths()) {
          varFilePathList.addAll(readManifestFilesFromDirectory(
              Paths.get(file.getAbsolutePath(), varPath).normalize().toString(), logCallback));
        }
        manifestsFromArtifactBundle.put(tasManifestDelegateConfig.getIdentifier(), varFilePathList);
      }
      if (tasManifestDelegateConfig.getAutoScalerPath() != null) {
        List<FileData> manifestFiles = readManifestFilesFromDirectory(
            Paths.get(file.getAbsolutePath(), tasManifestDelegateConfig.getAutoScalerPath().get(0))
                .normalize()
                .toString(),
            logCallback);
        manifestsFromArtifactBundle.put(tasManifestDelegateConfig.getIdentifier(), manifestFiles);
      }

      FileIo.deleteDirectoryAndItsContentIfExists(file.getAbsolutePath());
      return manifestsFromArtifactBundle;
    } catch (IOException e) {
      throw new UnexpectedException("Failed to get custom source manifest files", e);
    }
  }

  private File upzipFile(File artifactBundleFile, String activityId) throws IOException {
    InputStream inputStream = new FileInputStream(artifactBundleFile.getAbsolutePath());
    ZipInputStream zipInputStream = new ZipInputStream(inputStream);
    File tempDir = Files.createTempDir();
    String fileName = ARTIFACT_BUNDLE_MANIFESTS + activityId;
    createDirectoryIfDoesNotExist(tempDir + fileName);
    File file = new File(tempDir, fileName);
    unzipManifestFiles(file, zipInputStream);
    return file;
  }
  public List<FileData> readManifestFilesFromDirectory(String manifestFilesDirectory, LogCallback logCallback) {
    List<FileData> fileDataList;
    Path directory = Paths.get(manifestFilesDirectory);
    try {
      fileDataList = getFilesUnderPath(directory.toString());
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex));
      throw new UnexpectedException("Failed to get files. Error: " + ExceptionUtils.getMessage(ex));
    }

    List<FileData> manifestFiles = new ArrayList<>();
    for (FileData fileData : fileDataList) {
      String filePath = fileData.getFilePath();
      try {
        String fileContent = new String(fileData.getFileBytes(), UTF_8);
        if (isValidManifest(fileContent, logCallback)) {
          manifestFiles.add(FileData.builder().fileName(filePath).fileContent(fileContent).build());
        }
      } catch (Exception ex) {
        throw new UnexpectedException(String.format("Failed to read content of file %s. Error: %s",
            new File(filePath).getName(), ExceptionUtils.getMessage(ex)));
      }
    }

    checkDuplicateManifests(manifestFiles, logCallback);

    return manifestFiles;
  }
  private void checkDuplicateManifests(List<FileData> manifestFiles, LogCallback logCallback) {
    Map<ManifestType, Long> fileTypeCount = manifestFiles.stream().collect(
        Collectors.groupingBy(fd -> getManifestType(fd.getFileContent(), fd.getFileName(), logCallback), counting()));
    verifyMultipleCount(AUTOSCALAR_MANIFEST, fileTypeCount);
    verifyMultipleCount(APPLICATION_MANIFEST, fileTypeCount);
  }
  private void verifyMultipleCount(ManifestType manifestType, Map<ManifestType, Long> fileTypeCount) {
    if (fileTypeCount.getOrDefault(manifestType, 0L) > 1) {
      throw new UnexpectedException(String.format("Found more than %d counts of %s", 1, manifestType.getDescription()));
    }
  }

  public boolean isValidManifest(String fileContent, LogCallback logCallback) {
    ManifestType manifestType = getManifestType(fileContent, null, logCallback);
    return null != manifestType;
  }

  public ManifestType getManifestType(String content, @Nullable String fileName, LogCallback logCallback) {
    Map<String, Object> map;
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try {
      map = mapper.readValue(content, Map.class);
    } catch (Exception e) {
      log.warn(getParseErrorMessage(fileName, e.getMessage()));
      logCallback.saveExecutionLog(getParseErrorMessage(fileName), LogLevel.WARN);
      logCallback.saveExecutionLog("Error: " + e.getMessage(), LogLevel.WARN);
      return null;
    }

    if (isApplicationManifest(map)) {
      return APPLICATION_MANIFEST;
    }

    if (isVariableManifest(map)) {
      return VARIABLE_MANIFEST;
    }

    if (isAutoscalarManifest(map)) {
      return AUTOSCALAR_MANIFEST;
    }

    log.warn(getParseErrorMessage(fileName));
    logCallback.saveExecutionLog(getParseErrorMessage(fileName), LogLevel.WARN);
    return null;
  }
  private String getParseErrorMessage(String fileName) {
    return "Failed to parse file" + (isNotEmpty(fileName) ? " " + fileName : "") + ".";
  }

  private String getParseErrorMessage(String fileName, String errorMessage) {
    return String.format("Failed to parse file [%s]. Error - [%s]", isEmpty(fileName) ? "" : fileName, errorMessage);
  }

  private boolean isAutoscalarManifest(Map<String, Object> map) {
    return map.containsKey(PCF_AUTOSCALAR_MANIFEST_INSTANCE_LIMITS_ELE)
        && map.containsKey(PCF_AUTOSCALAR_MANIFEST_RULES_ELE);
  }

  private boolean isVariableManifest(Map<String, Object> map) {
    Optional entryOptional = map.entrySet().stream().filter(entry -> isInvalidValue(entry.getValue())).findFirst();

    return !entryOptional.isPresent();
  }

  private boolean isInvalidValue(Object value) {
    return value instanceof Map;
  }

  private boolean isApplicationManifest(Map<String, Object> map) {
    if (map.containsKey(APPLICATION_YML_ELEMENT)) {
      List<Map> applicationMaps = (List<Map>) map.get(APPLICATION_YML_ELEMENT);
      if (isEmpty(applicationMaps)) {
        return false;
      }

      Map application = applicationMaps.get(0);
      Map<String, Object> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      treeMap.putAll(application);
      return treeMap.containsKey(NAME_MANIFEST_YML_ELEMENT);
    }

    return false;
  }

  protected boolean isPackageArtifact(TasArtifactConfig tasArtifactConfig) {
    return TasArtifactType.PACKAGE == tasArtifactConfig.getArtifactType();
  }
}
