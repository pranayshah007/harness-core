/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.exception.KubernetesExceptionExplanation.FILE_PATH_NOT_PART_OF_MANIFEST_FORMAT;
import static io.harness.k8s.exception.KubernetesExceptionExplanation.NO_FILES_EXISTS_IN_MANIFEST_DIRECTORY;
import static io.harness.k8s.exception.KubernetesExceptionHints.AVAILABLE_FILES_IN_MANIFEST_DIRECTORY_FORMAT;
import static io.harness.k8s.exception.KubernetesExceptionHints.MAYBE_DID_YOU_MEAN_FILE_FORMAT;
import static io.harness.k8s.exception.KubernetesExceptionHints.NO_FILES_FOUND_IN_MANIFEST_DIRECTORY;
import static io.harness.k8s.exception.KubernetesExceptionMessages.UNABLE_TO_FIND_FILE_IN_MANIFEST_DIRECTORY_FORMAT;
import static io.harness.k8s.manifest.ManifestHelper.kustomizeFileNameYaml;
import static io.harness.k8s.manifest.ManifestHelper.kustomizeFileNameYml;
import static io.harness.k8s.manifest.ManifestHelper.yaml_file_extension;
import static io.harness.k8s.manifest.ManifestHelper.yml_file_extension;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class K8sTaskManifestValidator {
  private static final Integer DEFAULT_FILE_LIST_BUFFER_SIZE = 64;
  private static final Integer MAX_FILE_WALK_DEPTH = 16;
  private static final Integer MAX_DISPLAY_FILE_LIST = 8;
  private static final String TEMPLATES_FOLDER = "templates/";

  public static final Predicate<Path> IS_DIRECTORY = p -> new File(p.toString()).isDirectory();
  public static final Predicate<Path> IS_FILE = p -> new File(p.toString()).isFile();

  public static final Predicate<Path> IS_YAML_FILE = IS_FILE.and(p -> {
    String extension = FilenameUtils.getExtension(p.toString());
    if (isEmpty(extension)) {
      return false;
    }

    return yaml_file_extension.contains(extension) || yml_file_extension.contains(extension);
  });

  public static final Predicate<Path> IS_HELM_TEMPLATE_FILE =
      IS_YAML_FILE.and(p -> p.toString().contains(TEMPLATES_FOLDER));

  public static final Predicate<Path> IS_KUSTOMIZE_OVERLAY_FOLDER = IS_DIRECTORY.and(p -> {
    String kustomizationYamlPath = Paths.get(p.toString(), kustomizeFileNameYaml).toString();
    String kustomizationYmlPath = Paths.get(p.toString(), kustomizeFileNameYml).toString();

    return new File(kustomizationYamlPath).exists() || new File(kustomizationYmlPath).exists();
  });

  public void checkFilesPartOfManifest(
      String manifestDirectory, List<String> relativeFiles, Predicate<Path> fileFilter) {
    relativeFiles.forEach(file -> checkFilePartOfManifest(manifestDirectory, file, fileFilter));
  }

  public void checkFilePartOfManifest(String manifestDirectory, String relativeFile, Predicate<Path> fileFilter) {
    Path manifestPath = Paths.get(manifestDirectory);

    if (!new File(Paths.get(manifestDirectory, relativeFile).toString()).exists()) {
      Set<String> listFiles = listFiles(manifestPath, fileFilter);
      if (isEmpty(listFiles)) {
        throw NestedExceptionUtils.hintWithExplanationException(NO_FILES_FOUND_IN_MANIFEST_DIRECTORY,
            NO_FILES_EXISTS_IN_MANIFEST_DIRECTORY,
            new KubernetesTaskException(format(UNABLE_TO_FIND_FILE_IN_MANIFEST_DIRECTORY_FORMAT, relativeFile)));
      }

      List<String> orderedFilesByMatch = sortFilesByBestMatch(listFiles, relativeFile);
      String hintFileList = fileListToString(orderedFilesByMatch, listFiles.size());
      String hintBestMatch = orderedFilesByMatch.isEmpty() ? "" : orderedFilesByMatch.get(0);

      throw NestedExceptionUtils.hintWithExplanationException(
          format(AVAILABLE_FILES_IN_MANIFEST_DIRECTORY_FORMAT, hintFileList),
          format(FILE_PATH_NOT_PART_OF_MANIFEST_FORMAT, relativeFile),
          new HintException(
              format(MAYBE_DID_YOU_MEAN_FILE_FORMAT, isEmpty(hintBestMatch) ? "no files to suggest" : hintBestMatch),
              new KubernetesTaskException(format(UNABLE_TO_FIND_FILE_IN_MANIFEST_DIRECTORY_FORMAT, relativeFile))));
    }
  }

  private Set<String> listFiles(Path basePath, Predicate<Path> fileFilter) {
    try (Stream<Path> paths = Files.walk(basePath, MAX_FILE_WALK_DEPTH)) {
      return paths.filter(fileFilter).map(basePath::relativize).map(Path::toString).collect(Collectors.toSet());
    } catch (IOException e) {
      log.warn("Unable to list files in path {}. Exception: {}", basePath, e.getMessage());
      return Collections.emptySet();
    }
  }

  private List<String> sortFilesByBestMatch(Set<String> files, String match) {
    final LevenshteinDistance ld = LevenshteinDistance.getDefaultInstance();
    final Map<String, Integer> filesDistance = new HashMap<>();
    files.forEach(f -> filesDistance.put(f, ld.apply(f, match)));

    return filesDistance.entrySet()
        .stream()
        .sorted(Map.Entry.comparingByValue())
        .limit(MAX_DISPLAY_FILE_LIST)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  private String fileListToString(List<String> fileList, int totalFilesCount) {
    StringBuilder result = new StringBuilder(DEFAULT_FILE_LIST_BUFFER_SIZE);

    if (isNotEmpty(fileList)) {
      fileList.stream().map(file -> format("\t%s\n", file)).forEach(result::append);
      int totalFilesDiff = totalFilesCount - fileList.size();
      if (totalFilesDiff > 0) {
        result.append(format("\t... and (%d) more", totalFilesDiff));
      }
    } else {
      result.append("... no files found");
    }

    return result.toString();
  }
}
