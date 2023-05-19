/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.common.beans.UserSource;

import java.util.Map;
import java.util.Set;

import static io.harness.annotations.dev.HarnessTeam.PL;

@OwnedBy(PL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAccountLevelData {
  private Map<Generation, UserSource> sourceOfProvisioning;
  private Set<Generation> userProvisionedTo;

  public UserAccountLevelData(Map<Generation, UserSource> sourceOfProvisioning, Set<Generation> userProvisionedTo) {
    this.sourceOfProvisioning = sourceOfProvisioning;
    this.userProvisionedTo = userProvisionedTo;
  }

  public UserAccountLevelData() {
  }

  public static UserAccountLevelDataBuilder builder() {
    return new UserAccountLevelDataBuilder();
  }

  public Map<Generation, UserSource> getSourceOfProvisioning() {
    return this.sourceOfProvisioning;
  }

  public Set<Generation> getUserProvisionedTo() {
    return this.userProvisionedTo;
  }

  public void setSourceOfProvisioning(Map<Generation, UserSource> sourceOfProvisioning) {
    this.sourceOfProvisioning = sourceOfProvisioning;
  }

  public void setUserProvisionedTo(Set<Generation> userProvisionedTo) {
    this.userProvisionedTo = userProvisionedTo;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof UserAccountLevelData)) return false;
    final UserAccountLevelData other = (UserAccountLevelData) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$sourceOfProvisioning = this.getSourceOfProvisioning();
    final Object other$sourceOfProvisioning = other.getSourceOfProvisioning();
    if (this$sourceOfProvisioning == null ? other$sourceOfProvisioning != null : !this$sourceOfProvisioning.equals(other$sourceOfProvisioning))
      return false;
    final Object this$userProvisionedTo = this.getUserProvisionedTo();
    final Object other$userProvisionedTo = other.getUserProvisionedTo();
    if (this$userProvisionedTo == null ? other$userProvisionedTo != null : !this$userProvisionedTo.equals(other$userProvisionedTo))
      return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UserAccountLevelData;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $sourceOfProvisioning = this.getSourceOfProvisioning();
    result = result * PRIME + ($sourceOfProvisioning == null ? 43 : $sourceOfProvisioning.hashCode());
    final Object $userProvisionedTo = this.getUserProvisionedTo();
    result = result * PRIME + ($userProvisionedTo == null ? 43 : $userProvisionedTo.hashCode());
    return result;
  }

  public String toString() {
    return "UserAccountLevelData(sourceOfProvisioning=" + this.getSourceOfProvisioning() + ", userProvisionedTo=" + this.getUserProvisionedTo() + ")";
  }

  public static class UserAccountLevelDataBuilder {
    private Map<Generation, UserSource> sourceOfProvisioning;
    private Set<Generation> userProvisionedTo;

    UserAccountLevelDataBuilder() {
    }

    public UserAccountLevelDataBuilder sourceOfProvisioning(Map<Generation, UserSource> sourceOfProvisioning) {
      this.sourceOfProvisioning = sourceOfProvisioning;
      return this;
    }

    public UserAccountLevelDataBuilder userProvisionedTo(Set<Generation> userProvisionedTo) {
      this.userProvisionedTo = userProvisionedTo;
      return this;
    }

    public UserAccountLevelData build() {
      return new UserAccountLevelData(sourceOfProvisioning, userProvisionedTo);
    }

    public String toString() {
      return "UserAccountLevelData.UserAccountLevelDataBuilder(sourceOfProvisioning=" + this.sourceOfProvisioning + ", userProvisionedTo=" + this.userProvisionedTo + ")";
    }
  }
}
