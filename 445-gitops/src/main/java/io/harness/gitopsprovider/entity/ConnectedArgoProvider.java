/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitopsprovider.entity;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.GitOpsProviderType;
import io.harness.gitopsprovider.SearchTerm;
import io.harness.ng.DbAliases;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitopsproviders", noClassnameStored = true)
@Persistent
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.GITOPS)
public class ConnectedArgoProvider extends GitOpsProvider {
  @NotBlank @SearchTerm String adapterUrl;

  public ConnectedArgoProvider(String adapterUrl) {
    this.adapterUrl = adapterUrl;
    this.type = GitOpsProviderType.CONNECTED_ARGO_PROVIDER;
  }

  @Override
  public GitOpsProviderType getGitOpsProviderType() {
    return GitOpsProviderType.CONNECTED_ARGO_PROVIDER;
  }
}
