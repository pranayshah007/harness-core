/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.entities.modules;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.IACM)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "moduleLicenses", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.license.entities.module.IACMModuleLicense")
public class IACMModuleLicense extends ModuleLicense {
  private Integer numberOfDevelopers;
}
