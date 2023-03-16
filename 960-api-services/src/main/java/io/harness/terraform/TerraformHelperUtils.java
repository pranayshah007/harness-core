/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.provision.TerraformConstants.TERRAFORM_INTERNAL_FOLDER;
import static io.harness.provision.TerraformConstants.TERRAFORM_STATE_FILE_NAME;
import static io.harness.provision.TerraformConstants.WORKSPACE_DIR_BASE;
import static io.harness.provision.TerraformConstants.WORKSPACE_STATE_FILE_PATH_FORMAT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.filesystem.FileIo;
import io.harness.terraform.beans.TerraformVersion;

import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.JGitInternalException;

@UtilityClass
@OwnedBy(CDP)
@Slf4j
public class TerraformHelperUtils {
  public String generateCommandFlagsString(List<String> arguments, String command) {
    StringBuilder stringargs = new StringBuilder();
    if (isNotEmpty(arguments)) {
      for (String arg : arguments) {
        if (isNotEmpty(arg)) {
          stringargs.append(command).append(arg).append(' ');
        }
      }
    }
    return stringargs.toString();
  }

  public static String getGitExceptionMessageIfExists(Throwable t) {
    if (t instanceof JGitInternalException) {
      return Throwables.getRootCause(t).getMessage();
    }
    return ExceptionUtils.getMessage(t);
  }

  public void copyFilesToWorkingDirectory(String sourceDir, String destinationDir) throws IOException {
    File dest = new File(destinationDir);
    File src = new File(sourceDir);
    deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
    FileUtils.copyDirectory(src, dest);
    FileIo.waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  public void copytfCloudVarFilesToScriptDirectory(String sourceDir, String destinationDir) throws IOException {
    File dest = new File(destinationDir);
    File src = new File(sourceDir);

    if (sourceDir.contains(destinationDir)) {
      // this means inline var-files are located already in script-repository and has .auto.tfvars
      return;
    }

    String newVarFileCloudExt;
    if (!sourceDir.contains(".auto.tfvars")) {
      newVarFileCloudExt = sourceDir.replace(".tfvars", ".auto.tfvars");
    } else {
      // remote var-files can have already auto.tfvars
      newVarFileCloudExt = sourceDir;
    }

    File tfCloudVarFile = new File(newVarFileCloudExt);
    FileUtils.copyFile(src, tfCloudVarFile);
    FileUtils.copyFileToDirectory(tfCloudVarFile, dest);
    FileIo.waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  public void ensureLocalCleanup(String scriptDirectory) throws IOException {
    FileUtils.deleteQuietly(Paths.get(scriptDirectory, TERRAFORM_STATE_FILE_NAME).toFile());
    try {
      deleteDirectoryAndItsContentIfExists(Paths.get(scriptDirectory, TERRAFORM_INTERNAL_FOLDER).toString());
    } catch (IOException e) {
      log.warn("Failed to delete .terraform folder");
    }
    deleteDirectoryAndItsContentIfExists(Paths.get(scriptDirectory, WORKSPACE_DIR_BASE).toString());
  }

  public File getTerraformStateFile(String scriptDirectory, String workspace) {
    File tfStateFile = isEmpty(workspace)
        ? Paths.get(scriptDirectory, TERRAFORM_STATE_FILE_NAME).toFile()
        : Paths.get(scriptDirectory, format(WORKSPACE_STATE_FILE_PATH_FORMAT, workspace)).toFile();

    return tfStateFile.exists() ? tfStateFile : null;
  }

  public String createFileFromStringContent(String fileContent, String scriptDirectory, String fileName)
      throws IOException {
    UUID uuid = UUID.randomUUID();
    Path filePath = Files.createFile(Paths.get(scriptDirectory + "/" + format(fileName, uuid)));
    Files.write(filePath, fileContent.getBytes());
    return filePath.toString();
  }

  public String getAutoApproveArgument(TerraformVersion version) {
    return version.minVersion(0, 15) ? "-auto-approve" : "-force";
  }
}
