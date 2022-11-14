/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import java.io.IOException;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class ProgressRequestBody extends RequestBody {
  private final RequestBody requestBody;
  private final ProgressRequestListener progressListener;

  public ProgressRequestBody(RequestBody requestBody, ProgressRequestListener progressListener) {
    this.requestBody = requestBody;
    this.progressListener = progressListener;
  }

  @Override
  public MediaType contentType() {
    return requestBody.contentType();
  }

  @Override
  public long contentLength() throws IOException {
    return requestBody.contentLength();
  }

  @Override
  public void writeTo(BufferedSink sink) throws IOException {
    BufferedSink bufferedSink = Okio.buffer(sink(sink));
    requestBody.writeTo(bufferedSink);
    bufferedSink.flush();
  }

  private Sink sink(Sink sink) {
    return new ForwardingSink(sink) {
      long bytesWritten;
      long contentLength;

      @Override
      public void write(Buffer source, long byteCount) throws IOException {
        super.write(source, byteCount);
        if (contentLength == 0) {
          contentLength = contentLength();
        }

        bytesWritten += byteCount;
        progressListener.onRequestProgress(bytesWritten, contentLength, bytesWritten == contentLength);
      }
    };
  }

  public interface ProgressRequestListener {
    void onRequestProgress(long bytesWritten, long contentLength, boolean done);
  }
}
