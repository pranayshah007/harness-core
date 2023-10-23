/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.gcpccm;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@Persistent
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "GcpCloudCostConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.gcpccm.GcpCloudCostConfig")
@OwnedBy(CE)
public class GcpCloudCostConfig extends Connector {
  @NotEmpty List<CEFeatures> featuresEnabled;
  @NotNull String projectId;
  @NotNull String serviceAccountEmail;
  @Nullable GcpBillingExportDetails billingExportDetails;
}
