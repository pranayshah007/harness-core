/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.changeSource;

import io.harness.mongo.index.FdIndex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("PAGER_DUTY")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "PagerDutyChangeSourceKeys")
@EqualsAndHashCode(callSuper = true)
public class PagerDutyChangeSource extends ChangeSource {
  @NotNull @FdIndex private String connectorIdentifier;
  @NotNull private String pagerDutyServiceId;

  public static class UpdatablePagerDutyChangeSourceEntity
      extends UpdatableChangeSourceEntity<PagerDutyChangeSource, PagerDutyChangeSource> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<PagerDutyChangeSource> updateOperations, PagerDutyChangeSource harnessPagerDutyChangeSource) {
      setCommonOperations(updateOperations, harnessPagerDutyChangeSource);
      updateOperations
          .set(PagerDutyChangeSourceKeys.connectorIdentifier, harnessPagerDutyChangeSource.getConnectorIdentifier())
          .set(PagerDutyChangeSourceKeys.pagerDutyServiceId, harnessPagerDutyChangeSource.getPagerDutyServiceId());
    }
  }
}
