/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client.sshj;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import lombok.AllArgsConstructor;
import net.schmizz.sshj.xfer.InMemoryDestFile;

@AllArgsConstructor
public class StreamingInMemoryDestFile extends InMemoryDestFile implements Closeable {
  private ByteArrayOutputStream outputStream;

  @Override
  public long getLength() {
    return -1;
  }

  @Override
  public ByteArrayOutputStream getOutputStream() {
    return this.outputStream;
  }

  @Override
  public OutputStream getOutputStream(boolean append) {
    return this.outputStream;
  }

  @Override
  public void close() throws IOException {
    this.outputStream.close();
  }
}