/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"webhookBody", "webhookHeaders"})
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "gitWebhookRequestAttributesNG", noClassnameStored = true)
@Document("gitWebhookRequestAttributesNG")
@TypeAlias("io.harness.gitsync.core.beans.gitWebhookRequestAttributes")
@OwnedBy(DX)
public class GitWebhookRequestAttributes {
  private String webhookBody;
  private String webhookHeaders;
  @NotEmpty private String branchName;
  private String gitConnectorId;
  @NotEmpty private String repo;
  String headCommitId;
}
