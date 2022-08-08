/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;

/**
 * Created by anubhaw on 2/22/17.
 */
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class HostValidationResponse implements DelegateResponseData {
  private String hostName;
  private String status;
  private String errorCode;
  private String errorDescription;

  /**
   * Gets host name.
   *
   * @return the host name
   */
  public String getHostName() {
    return hostName;
  }

  /**
   * Sets host name.
   *
   * @param hostName the host name
   */
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  /**
   * Gets status.
   *
   * @return the status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Sets status.
   *
   * @param status the status
   */
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Gets error code.
   *
   * @return the error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Sets error code.
   *
   * @param errorCode the error code
   */
  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  /**
   * Gets error description.
   *
   * @return the error description
   */
  public String getErrorDescription() {
    return errorDescription;
  }

  /**
   * Sets error description.
   *
   * @param errorDescription the error description
   */
  public void setErrorDescription(String errorDescription) {
    this.errorDescription = errorDescription;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String hostName;
    private String status;
    private String errorCode;
    private String errorDescription;

    private Builder() {}

    /**
     * A host validation response builder.
     *
     * @return the builder
     */
    public static Builder aHostValidationResponse() {
      return new Builder();
    }

    /**
     * With host name builder.
     *
     * @param hostName the host name
     * @return the builder
     */
    public Builder withHostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    /**
     * With status builder.
     *
     * @param status the status
     * @return the builder
     */
    public Builder withStatus(String status) {
      this.status = status;
      return this;
    }

    /**
     * With error code builder.
     *
     * @param errorCode the error code
     * @return the builder
     */
    public Builder withErrorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    /**
     * With error description builder.
     *
     * @param errorDescription the error description
     * @return the builder
     */
    public Builder withErrorDescription(String errorDescription) {
      this.errorDescription = errorDescription;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aHostValidationResponse()
          .withHostName(hostName)
          .withStatus(status)
          .withErrorCode(errorCode)
          .withErrorDescription(errorDescription);
    }

    /**
     * Build host validation response.
     *
     * @return the host validation response
     */
    public HostValidationResponse build() {
      HostValidationResponse hostValidationResponse = new HostValidationResponse();
      hostValidationResponse.setHostName(hostName);
      hostValidationResponse.setStatus(status);
      hostValidationResponse.setErrorCode(errorCode);
      hostValidationResponse.setErrorDescription(errorDescription);
      return hostValidationResponse;
    }
  }
}
