/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.events.servicelevelobjective;

import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class AbstractServiceLevelObjectiveEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;

  public enum ServiceLevelObjectiveEventTypes {
    CREATE("ServiceLevelObjectiveCreateEvent"),
    UPDATE("ServiceLevelObjectiveUpdateEvent"),
    DELETE("ServiceLevelObjectiveDeleteEvent");

    private final String displayName;
    ServiceLevelObjectiveEventTypes(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return this.displayName;
    }
  }

  @Override
  public ResourceScope getResourceScope() {
    Preconditions.checkNotNull(accountIdentifier);
    Preconditions.checkNotNull(orgIdentifier);
    Preconditions.checkNotNull(projectIdentifier);
    return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
