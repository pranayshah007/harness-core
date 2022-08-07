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
public class SimpleAwsErrorHandler implements HttpResponseHandler<AmazonServiceException> {
  /**
   * See {@link HttpResponseHandler}, method needsConnectionLeftOpen()
   */
  private boolean needsConnectionLeftOpen;

  /**
   * Ctor.
   * @param connectionLeftOpen Should the connection be closed immediately or not?
   */
  public SimpleAwsErrorHandler(boolean connectionLeftOpen) {
    this.needsConnectionLeftOpen = connectionLeftOpen;
  }

  @Override
  public AmazonServiceException handle(HttpResponse response) {
    AmazonServiceException ase = new AmazonServiceException(response.getStatusText());
    ase.setStatusCode(response.getStatusCode());
    return ase;
  }

  @Override
  public boolean needsConnectionLeftOpen() {
    return this.needsConnectionLeftOpen;
  }
}
