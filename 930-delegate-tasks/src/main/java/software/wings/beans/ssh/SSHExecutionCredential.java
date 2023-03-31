/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.MoreObjects;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The type Ssh execution credential.
 *
 * @author Rishi
 */
@JsonTypeName("SSH")
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
@OwnedBy(CDP)
public class SSHExecutionCredential extends ExecutionCredential {
  private String sshUser;
  private char[] sshPassword;
  private String appAccount;
  private char[] appAccountPassword;
  private char[] keyPassphrase;

  /**
   * Instantiates a new Ssh execution credential.
   */
  public SSHExecutionCredential() {
    super(ExecutionType.SSH);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("sshUser", sshUser).add("appAccount", appAccount).toString();
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String sshUser;
    private char[] sshPassword;
    private String appAccount;
    private char[] appAccountPassword;
    private char[] keyPassphrase;
    private ExecutionType executionType;

    private Builder() {}

    /**
     * A ssh execution credential builder.
     *
     * @return the builder
     */
    public static Builder aSSHExecutionCredential() {
      return new Builder();
    }

    /**
     * With ssh user builder.
     *
     * @param sshUser the ssh user
     * @return the builder
     */
    public Builder withSshUser(String sshUser) {
      this.sshUser = sshUser;
      return this;
    }

    /**
     * With ssh password builder.
     *
     * @param sshPassword the ssh password
     * @return the builder
     */
    public Builder withSshPassword(char[] sshPassword) {
      this.sshPassword = sshPassword == null ? null : sshPassword.clone();
      return this;
    }

    /**
     * With app account builder.
     *
     * @param appAccount the app account
     * @return the builder
     */
    public Builder withAppAccount(String appAccount) {
      this.appAccount = appAccount;
      return this;
    }

    /**
     * With app account password builder.
     *
     * @param appAccountPassword the app account password
     * @return the builder
     */
    public Builder withAppAccountPassword(char[] appAccountPassword) {
      this.appAccountPassword = appAccountPassword == null ? null : appAccountPassword.clone();
      return this;
    }

    /**
     * With key passphrase builder.
     *
     * @param keyPassphrase the key passphrase
     * @return the builder
     */
    public Builder withKeyPassphrase(char[] keyPassphrase) {
      this.keyPassphrase = keyPassphrase == null ? null : keyPassphrase.clone();
      return this;
    }

    /**
     * With execution type builder.
     *
     * @param executionType the execution type
     * @return the builder
     */
    public Builder withExecutionType(ExecutionType executionType) {
      this.executionType = executionType;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aSSHExecutionCredential()
          .withSshUser(sshUser)
          .withSshPassword(sshPassword)
          .withAppAccount(appAccount)
          .withAppAccountPassword(appAccountPassword)
          .withKeyPassphrase(keyPassphrase)
          .withExecutionType(executionType);
    }

    /**
     * Build ssh execution credential.
     *
     * @return the ssh execution credential
     */
    public SSHExecutionCredential build() {
      SSHExecutionCredential sSHExecutionCredential = new SSHExecutionCredential();
      sSHExecutionCredential.setSshUser(sshUser);
      sSHExecutionCredential.setSshPassword(sshPassword);
      sSHExecutionCredential.setAppAccount(appAccount);
      sSHExecutionCredential.setAppAccountPassword(appAccountPassword);
      sSHExecutionCredential.setKeyPassphrase(keyPassphrase);
      sSHExecutionCredential.setExecutionType(executionType);
      return sSHExecutionCredential;
    }
  }
}
