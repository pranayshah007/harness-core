/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.http.HttpResponseHandler;
import java.io.IOException;
import java.io.StringWriter;
import org.apache.commons.io.IOUtils;

/**
 * A simple aws response handler that only checks that the http status is within the 200 range.
 * If not, {@link AmazonServiceException} is thrown.
 * @author Mihai Andronache (amihaiemil@gmail.com)
 * @version $Id$
 * @since 1.0.0
 *
 */
public class SimpleAwsResponseHandler implements HttpResponseHandler<HttpResponse> {
  /**
   * See {@link HttpResponseHandler}, method needsConnectionLeftOpen()
   */
  private boolean needsConnectionLeftOpen;

  /**
   * Ctor.
   * @param connectionLeftOpen Should the connection be closed immediately or not?
   */
  public SimpleAwsResponseHandler(boolean connectionLeftOpen) {
    this.needsConnectionLeftOpen = connectionLeftOpen;
  }

  @Override
  public HttpResponse handle(HttpResponse response) {
    int status = response.getStatusCode();
    if (status < 200 || status >= 300) {
      String content;
      final StringWriter writer = new StringWriter();
      try {
        IOUtils.copy(response.getContent(), writer, "UTF-8");
        content = writer.toString();
      } catch (final IOException e) {
        content = "Couldn't get response content!";
      }
      AmazonServiceException ase = new AmazonServiceException(content);
      ase.setStatusCode(status);
      throw ase;
    }

    return response;
  }

  @Override
  public boolean needsConnectionLeftOpen() {
    return this.needsConnectionLeftOpen;
  }
}
