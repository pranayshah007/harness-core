package io.harness.ng.core.buckets.resources;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.validation.constraints.NotNull;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class BucketsResourceUtils {
  @Inject private ServiceEntityService serviceEntityService;
  @NotNull
  public StoreConfig locateStoreConfigInService(
      String accountId, String orgId, String projectId, String serviceRef, String BucketFqn) {
    YamlNode bucketLeafNode =
        serviceEntityService.getYamlNodeForFqn(accountId, orgId, projectId, serviceRef, BucketFqn);

    YamlNode StoreConfigNode = bucketLeafNode.getParentNode().getParentNode();

    final StoreConfigInternalDTO storeConfigInternalDTO;
    try {
      storeConfigInternalDTO = YamlUtils.read(StoreConfigNode.toString(), StoreConfigInternalDTO.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Unable to read store config  in service yaml", e);
    }

    return storeConfigInternalDTO.spec;
  }

  static class StoreConfigInternalDTO {
    @JsonProperty("type") StoreConfigType type;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    private StoreConfig spec;
  }
}
