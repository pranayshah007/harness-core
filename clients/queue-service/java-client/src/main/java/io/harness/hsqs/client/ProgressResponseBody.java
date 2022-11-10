/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.ResponseBody;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;

public class ProgressResponseBody extends ResponseBody {
  private final ResponseBody responseBody;
  private final ProgressListener progressListener;
  private BufferedSource bufferedSource;

  public ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
    this.responseBody = responseBody;
    this.progressListener = progressListener;
  }

  @Override
  public MediaType contentType() {
    return responseBody.contentType();
  }

  @Override
  public long contentLength() throws IOException {
    return responseBody.contentLength();
  }

  @Override
  public BufferedSource source() throws IOException {
    if (bufferedSource == null) {
      bufferedSource = Okio.buffer(source(responseBody.source()));
    }
    return bufferedSource;
  }

  private Source source(Source source) {
    return new ForwardingSource(source) {
      long totalBytesRead;

      @Override
      public long read(Buffer sink, long byteCount) throws IOException {
        long bytesRead = super.read(sink, byteCount);
        // read() returns the number of bytes read, or -1 if this source is exhausted.
        totalBytesRead += bytesRead != -1 ? bytesRead : 0;
        progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
        return bytesRead;
      }
    };
  }

  public interface ProgressListener {
    void update(long bytesRead, long contentLength, boolean done);
  }
}
