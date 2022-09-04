/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "managerConfiguration", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ManagerConfiguration extends Base {
  public static final String GLOBAL_CONFIG_ID = "__GLOBAL_CONFIG_ID__";
  public static final String MATCH_ALL_VERSION = "*";
  String primaryVersion;

  public static final class Builder {
    String primaryVersion;

    private Builder() {}

    public static ManagerConfiguration.Builder aManagerConfiguration() {
      return new ManagerConfiguration.Builder();
    }

    public ManagerConfiguration.Builder withPrimaryVersion(String primaryVersion) {
      this.primaryVersion = primaryVersion;
      return this;
    }

    public ManagerConfiguration build() {
      ManagerConfiguration managerConfiguration = new ManagerConfiguration();
      managerConfiguration.setUuid(GLOBAL_CONFIG_ID);
      managerConfiguration.setPrimaryVersion(primaryVersion);
      return managerConfiguration;
    }
  }
}
