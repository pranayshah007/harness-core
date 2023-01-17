/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm.constants;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;

public class CopyManifestFilesHelper {
    public static void copyManifestFilesToWorkingDir(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            FileUtils.copyDirectory(src, dest);
        } else {
            Path destFilePath = Paths.get(dest.getPath(), src.getName());
            FileUtils.copyFile(src, destFilePath.toFile());
        }
        deleteDirectoryAndItsContentIfExists(src.getAbsolutePath());
        waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
    }
}
