/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@OwnedBy(HarnessTeam.DX)
public class FilePathUtils {
  public static final String FILE_PATH_SEPARATOR = "/";
  public static final Pattern FILE_PATH_PATTERN = Pattern.compile("^(\\b(account|org)\\b:)*/.+$");

  public static boolean isFilePartOfFolder(String folderPath, String filePath) {
    if (filePath == null || folderPath == null) {
      throw new InvalidRequestException(
          String.format("filePath : %s or folderPath : %s cannot be null", filePath, folderPath));
    }

    return trimPath(filePath).startsWith(trimPath(folderPath));
  }

  public static List<String> getAllFilesWithinFolder(String folderPath, List<String> filePaths) {
    if (filePaths == null || folderPath == null) {
      throw new InvalidRequestException(
          String.format("filePath list : %s or folderPath : %s cannot be null", filePaths, folderPath));
    }
    List<String> validFilePaths = new ArrayList<>();
    filePaths.forEach(filePath -> {
      if (isFilePartOfFolder(folderPath, filePath)) {
        validFilePaths.add(filePath);
      }
    });
    return validFilePaths;
  }

  public static String removeTrailingChars(String path, String chars) {
    path = path.replaceAll(chars + "+$", "");
    return path;
  }

  public static String updatePathWithForwardSlash(String filePath) {
    if (filePath.charAt(0) != '/') {
      return "/" + filePath;
    }
    return filePath;
  }

  public static String addEndingSlashIfMissing(String filePath) {
    if (isNotEmpty(filePath) && filePath.endsWith("/")) {
      return filePath;
    }
    return filePath + "/";
  }

  public static String removeStartingAndEndingSlash(String path) {
    if (path == null) {
      throw new InvalidRequestException("Path cannot be null.");
    }
    path = StringUtils.stripStart(path, FILE_PATH_SEPARATOR);
    return StringUtils.stripEnd(path, FILE_PATH_SEPARATOR);
  }

  public boolean isScopedFilePath(final String path) {
    if (isEmpty(path)) {
      return false;
    }

    return FILE_PATH_PATTERN.matcher(path).find();
  }

  // ---------------------------------- PRIVATE METHODS ----------------------------

  // Remove starting and ending backslashes
  private String trimPath(String path) {
    path = path.replaceAll("^/+", "");
    path = path.replaceAll("/+$", "");
    return path;
  }

  public static String addStartingSlashIfMissing(String path) {
    if (path.charAt(0) != '/') {
      return "/" + path;
    }
    return path;
  }
}
