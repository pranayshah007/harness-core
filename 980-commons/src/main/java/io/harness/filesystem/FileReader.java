/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;

public interface FileReader {
  String getFileContent(String filePath, Charset charset) throws IOException, InvalidPathException;
  byte[] getFileBytes(String filePath) throws IOException, InvalidPathException;
  InputStream newInputStream(String filePath, OpenOption openOption) throws IOException, InvalidPathException;
}
