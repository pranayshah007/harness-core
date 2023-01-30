/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executorlib.serviceproviders;

import com.google.inject.Singleton;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;

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
    public String getFileIdByVersion(FileBucket fileBucket, String entityId, int version, String accountId) throws IOException {
        return null;
    }

    @Override
    public InputStream downloadByFileId(FileBucket bucket, String fileId, String accountId) throws IOException {
        return null;
    }

    @Override
    public InputStream downloadByConfigFileId(String fileId, String accountId, String appId, String activityId) throws IOException {
        return null;
    }

    @Override
    public DelegateFile getMetaInfo(FileBucket fileBucket, String fileId, String accountId) throws IOException {
        return null;
    }

    @Override
    public DelegateFile uploadAsFile(DelegateFile delegateFile, File File) {
        return null;
    }
}
