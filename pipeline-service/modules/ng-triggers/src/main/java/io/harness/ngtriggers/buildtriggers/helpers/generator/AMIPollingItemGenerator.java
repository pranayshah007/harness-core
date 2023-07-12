/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.AMIFilterPayload;
import io.harness.polling.contracts.AMIPayload;
import io.harness.polling.contracts.AMITagPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDC)
public class AMIPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);

    String connectorRef = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.connectorRef");

    String region = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.region");

    String versionRegex = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.versionRegex");

    List<AMITag> tags = buildTriggerHelper.validateAndFetchTagsListFromJsonNode(buildTriggerOpsData, "spec.tags");

    List<AMIFilter> filters =
        buildTriggerHelper.validateAndFetchFiltersListFromJsonNode(buildTriggerOpsData, "spec.filters");

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.AMI)
                                   .setAmiPayload(AMIPayload.newBuilder()
                                                      .setRegion(region)
                                                      .addAllFilters(mapToAMIFilterPayload(filters))
                                                      .addAllTags(mapToAMITagPayload(tags))
                                                      .setVersionRegex(versionRegex)
                                                      .build())
                                   .build())
        .build();
  }

  public List<AMITagPayload> mapToAMITagPayload(List<AMITag> tags) {
    List<AMITagPayload> tagsPayload = new ArrayList<>();

    for (AMITag tag : tags) {
      String name = tag.getName();

      String value = tag.getValue();

      AMITagPayload tagPayload = AMITagPayload.newBuilder().setName(name).setValue(value).build();

      tagsPayload.add(tagPayload);
    }

    return tagsPayload;
  }

  public List<AMIFilterPayload> mapToAMIFilterPayload(List<AMIFilter> filters) {
    List<AMIFilterPayload> filtersPayload = new ArrayList<>();

    for (AMIFilter filter : filters) {
      String name = filter.getName();

      String value = filter.getValue();

      AMIFilterPayload filterPayload = AMIFilterPayload.newBuilder().setName(name).setValue(value).build();

      filtersPayload.add(filterPayload);
    }

    return filtersPayload;
  }
}
