/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.FileUploadLimit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Singleton
@OwnedBy(CDP)
public class FileStoreConfiguration extends Configuration {
  @JsonProperty private FileUploadLimit fileUploadLimits = new FileUploadLimit();
}
