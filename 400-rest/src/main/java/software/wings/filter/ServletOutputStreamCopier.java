/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpServletResponseCopier that creates a copy of the http response payload. Based on
 * https://github.com/ukwa/interject/blob/master/interject-servlet-filter
 * /src/main/java/uk/bl/wa/interject/filter/ServletOutputStreamCopier.java
 *
 * @author Rishi
 */
@Slf4j
public class ServletOutputStreamCopier extends ServletOutputStream {
  private OutputStream outputStream;
  private ByteArrayOutputStream copy;

  /**
   * Instantiates a new servlet output stream copier.
   *
   * @param outputStream the output stream
   */
  public ServletOutputStreamCopier(OutputStream outputStream) {
    this.outputStream = outputStream;
    this.copy = new ByteArrayOutputStream(1024);
  }

  /* (non-Javadoc)
   * @see java.io.OutputStream#write(int)
   */
  @Override
  public void write(int b) throws IOException {
    outputStream.write(b);
    copy.write(b);
  }

  /**
   * Get copy byte [ ].
   *
   * @return the byte [ ]
   */
  public byte[] getCopy() {
    return copy.toByteArray();
  }

  /**
   * Flush stream.
   */
  public void flushStream() throws IOException {
    if (null != copy) {
      copy.close();
    }
  }

  @Override
  public boolean isReady() {
    return false;
  }

  @Override
  public void setWriteListener(WriteListener arg0) {}
}
