/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client.sshj;

import io.harness.shell.AbstractScriptExecutor;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import net.schmizz.sshj.xfer.InMemorySourceFile;

public class StreamingInMemorySourceFile extends InMemorySourceFile implements Closeable {
  private final ByteArrayOutputStream byteArrayOutputStream;
  private final BufferedInputStream inputStream;
  private final String fileName;
  private final long length;

  public StreamingInMemorySourceFile(AbstractScriptExecutor.FileProvider fileProvider)
      throws IOException, ExecutionException {
    this.fileName = fileProvider.getInfo().getKey();
    this.length = fileProvider.getInfo().getValue();
    this.byteArrayOutputStream = new ByteArrayOutputStream();
    fileProvider.downloadToStream(byteArrayOutputStream);
    this.inputStream = new BufferedInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
  }

  @Override
  public String getName() {
    return this.fileName;
  }

  @Override
  public long getLength() {
    return this.length;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return this.inputStream;
  }

  @Override
  public void close() throws IOException {
    this.byteArrayOutputStream.close();
    this.inputStream.close();
  }
}