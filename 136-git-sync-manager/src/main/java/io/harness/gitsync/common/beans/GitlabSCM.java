/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.ng.DbAliases;
import io.harness.ng.userprofile.commons.SCMType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "GitlabSCMKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "userSourceCodeManagers", noClassnameStored = true)
@TypeAlias("io.harness.gitsync.common.beans.GitlabSCM")
@Persistent
public class GitlabSCM extends UserSourceCodeManager implements PersistentRegularIterable {
  GitlabApiAccess gitlabApiAccess;
  GitlabApiAccessType apiAccessType;
  @NonFinal Long nextTokenRenewIteration;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (GitlabSCMKeys.nextTokenRenewIteration.equals(fieldName)) {
      return nextTokenRenewIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (GitlabSCMKeys.nextTokenRenewIteration.equals(fieldName)) {
      this.nextTokenRenewIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public String getUuid() {
    return getId();
  }

  @Override
  public SCMType getType() {
    return SCMType.GITLAB;
  }
}