/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import static software.wings.settings.SettingVariableTypes.BASTION_HOST_CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.TargetModule;
import io.harness.shell.AccessType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 5/17/16.
 */
@JsonTypeName("BASTION_HOST_CONNECTION_ATTRIBUTES")
@Data
@EqualsAndHashCode(callSuper = false)
@TargetModule(_957_CG_BEANS)
public class BastionConnectionAttributes extends HostConnectionAttributes {
  @NotEmpty private String hostName;

  /**
   * Instantiates a new bastion connection attributes.
   */
  public BastionConnectionAttributes() {
    super(BASTION_HOST_CONNECTION_ATTRIBUTES);
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private ConnectionType connectionType;
    private AccessType accessType;
    private String hostName;
    private char[] key;
    private String userName;
    private String accountId;

    private Builder() {}

    /**
     * A bastion connection attributes builder.
     *
     * @return the builder
     */
    public static Builder aBastionConnectionAttributes() {
      return new Builder();
    }

    /**
     * With connection type builder.
     *
     * @param connectionType the connection type
     * @return the builder
     */
    public Builder withConnectionType(ConnectionType connectionType) {
      this.connectionType = connectionType;
      return this;
    }

    /**
     * With access type builder.
     *
     * @param accessType the access type
     * @return the builder
     */
    public Builder withAccessType(AccessType accessType) {
      this.accessType = accessType;
      return this;
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
     * With key builder.
     *
     * @param key the key
     * @return the builder
     */
    public Builder withKey(char[] key) {
      this.key = key == null ? null : key.clone();
      return this;
    }

    /**
     * With user name builder.
     *
     * @param userName the user name
     * @return the builder
     */
    public Builder withUserName(String userName) {
      this.userName = userName;
      return this;
    }

    /**
     * With accountId.
     *
     * @param accountId the accountId
     * @return the builder
     */
    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aBastionConnectionAttributes()
          .withConnectionType(connectionType)
          .withAccessType(accessType)
          .withHostName(hostName)
          .withKey(key)
          .withUserName(userName)
          .withAccountId(accountId);
    }

    /**
     * Build bastion connection attributes.
     *
     * @return the bastion connection attributes
     */
    public BastionConnectionAttributes build() {
      BastionConnectionAttributes bastionConnectionAttributes = new BastionConnectionAttributes();
      bastionConnectionAttributes.setConnectionType(connectionType);
      bastionConnectionAttributes.setAccessType(accessType);
      bastionConnectionAttributes.setHostName(hostName);
      bastionConnectionAttributes.setKey(key);
      bastionConnectionAttributes.setUserName(userName);
      bastionConnectionAttributes.setAccountId(accountId);
      return bastionConnectionAttributes;
    }
  }
}
