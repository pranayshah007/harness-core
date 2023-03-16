/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.serviceproviders;

import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;

import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Singleton
public class DelegateFileManagerNoopImpl implements DelegateFileManagerBase {
  @Override
  public DelegateFile upload(DelegateFile delegateFile, InputStream contentSource) {
    return null;
  }

  @Override
  public String getFileIdByVersion(FileBucket fileBucket, String entityId, int version, String accountId)
      throws IOException {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream downloadByFileId(FileBucket bucket, String fileId, String accountId) throws IOException {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream downloadByConfigFileId(String fileId, String accountId, String appId, String activityId)
      throws IOException {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public DelegateFile getMetaInfo(FileBucket fileBucket, String fileId, String accountId) throws IOException {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public DelegateFile uploadAsFile(DelegateFile delegateFile, File File) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }
}
