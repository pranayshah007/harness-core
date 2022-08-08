/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.governance;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonCreator;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@ToString
@EqualsAndHashCode
@OwnedBy(HarnessTeam.CDC)
public abstract class GovernanceFreezeConfig implements UuidAware {
  // not used
  @EqualsAndHashCode.Exclude private boolean freezeForAllApps;
  @EqualsAndHashCode.Exclude private List<String> appIds;
  @EqualsAndHashCode.Exclude private List<EnvironmentType> environmentTypes;

  private String uuid;
  @NotEmpty private String name;
  private String description;
  @EqualsAndHashCode.Exclude private boolean applicable; // Corresponds to the on/off button for each window
  private List<ApplicationFilter> appSelections;
  private List<String> userGroups; // User groups to be notified
  private UserGroupFilter userGroupSelection;

  @JsonCreator
  public GovernanceFreezeConfig(@JsonProperty("freezeForAllApps") boolean freezeForAllApps,
      @JsonProperty("appIds") List<String> appIds,
      @JsonProperty("environmentTypes") List<EnvironmentType> environmentTypes, @JsonProperty("name") String name,
      @JsonProperty("description") String description, @JsonProperty("applicable") boolean applicable,
      @JsonProperty("appSelections") List<ApplicationFilter> appSelections,
      @JsonProperty("userGroups") List<String> userGroups, @JsonProperty("uuid") String uuid,
      @JsonProperty("userGroupSelection") UserGroupFilter userGroupSelection) {
    this.freezeForAllApps = freezeForAllApps;
    this.appIds = appIds;
    this.environmentTypes = environmentTypes;
    this.name = name;
    this.description = description;
    this.applicable = applicable;
    this.appSelections = appSelections;
    this.uuid = EmptyPredicate.isEmpty(uuid) ? generateUuid() : uuid;
    this.userGroups = userGroups;
    this.userGroupSelection = userGroupSelection;
  }

  public List<String> getAppIds() {
    return CollectionUtils.emptyIfNull(appIds);
  }

  public List<EnvironmentType> getEnvironmentTypes() {
    return CollectionUtils.emptyIfNull(environmentTypes);
  }
  public boolean isFreezeForAllApps() {
    return freezeForAllApps;
  }

  public abstract long fetchEndTime();

  // this method takes into account applicable boolean as well
  public boolean equalsWithApplicable(final Object o) {
    if (!this.equals(o)) {
      return false;
    }
    return ((GovernanceFreezeConfig) o).applicable == this.isApplicable();
  }
}
