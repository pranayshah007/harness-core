/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.INLINE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.logging.LogCallback;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@OwnedBy(CDP)
public class TerragruntDownloadService {
  private static final Set<String> SUPPORTED_STORE_TYPES = ImmutableSet.of(GIT.name(), INLINE.name());

  @Inject private GitStoreDownloadService gitStoreDownloadService;
  @Inject private InlineStoreDownloadService inlineStoreDownloadService;

  public DownloadResult download(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException {
    FileStoreDownloadService downloadService = getDownloadService(storeConfig);
    return downloadService.download(storeConfig, accountId, outputDirectory, logCallback);
  }

  public FetchFilesResult fetchFiles(StoreDelegateConfig storeConfig, String accountId, String outputDirectory,
      LogCallback logCallback) throws IOException {
    FileStoreDownloadService downloadService = getDownloadService(storeConfig);
    return downloadService.fetchFiles(storeConfig, accountId, outputDirectory, logCallback);
  }

  private FileStoreDownloadService getDownloadService(StoreDelegateConfig storeConfig) {
    switch (storeConfig.getType()) {
      case GIT:
        return gitStoreDownloadService;
      case INLINE:
        return inlineStoreDownloadService;
      default:
        throw NestedExceptionUtils.hintWithExplanationException(
            format("Use one of supported store: [%s]", SUPPORTED_STORE_TYPES.toString()),
            format("Store '%s' is not yet supported yet", GIT),
            new InvalidArgumentsException(Pair.of("storeConfig", "Unsupported store type")));
    }
  }
}
