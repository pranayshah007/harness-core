/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "deploymentStepTimeSeriesEventQueue", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class DeploymentStepTimeSeriesEvent extends Queuable {
  private TimeSeriesEventInfo timeSeriesEventInfo;
}
