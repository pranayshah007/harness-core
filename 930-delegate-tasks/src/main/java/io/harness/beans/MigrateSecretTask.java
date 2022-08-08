/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.queue.Queuable;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by rsingh on 10/6/17.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "kmsTransitionEvent2", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class MigrateSecretTask extends Queuable {
  @NotNull private String accountId;
  @NotNull private String secretId;
  @NotNull private SecretManagerConfig fromConfig;
  @NotNull private SecretManagerConfig toConfig;
}
