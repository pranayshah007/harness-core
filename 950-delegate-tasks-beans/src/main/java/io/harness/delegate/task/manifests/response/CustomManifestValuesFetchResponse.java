/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.manifests.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manifest.CustomSourceFile;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CustomManifestValuesFetchResponse implements DelegateTaskNotifyResponseData {
  CommandExecutionStatus commandExecutionStatus;
  @Nullable Map<String, Collection<CustomSourceFile>> valuesFilesContentMap;
  // Nullable for now as errorMessage is not getting used in K8s CustomValueFetchTask
  @Nullable String errorMessage;
  @NonFinal @Setter String zippedManifestFileId;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
  @Setter private UnitProgressData unitProgressData;
}
