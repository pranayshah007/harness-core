package io.harness.cache;

import io.harness.app.beans.entities.CacheMetadataInfo;

public interface CICacheManagementService {
    CacheMetadataInfo getCacheMetadata(String accountId);
}
