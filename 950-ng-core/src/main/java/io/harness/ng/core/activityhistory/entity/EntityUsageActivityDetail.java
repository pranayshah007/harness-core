/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.entity;

import io.harness.annotations.StoreIn;
import io.harness.connector.ConnectivityStatus;
import io.harness.ng.DbAliases;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.entityusageactivity.EntityUsageDetail;

import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "entityActivity", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.ng.core.activity.EntityUsageActivityDetail")
public class EntityUsageActivityDetail extends NGActivity {
  @NotBlank String referredByEntityFQN;
  @NotBlank String referredByEntityType;
  @NotNull EntityDetail referredByEntity;
  EntityUsageDetail usageDetail;
  @NotEmpty String activityStatusMessage;
  List<ErrorDetail> errors;
  String errorSummary;
  ConnectivityStatus status;
}
