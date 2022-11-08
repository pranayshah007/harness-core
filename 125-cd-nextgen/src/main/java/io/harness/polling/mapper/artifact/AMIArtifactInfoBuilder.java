/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.AMIArtifactInfo;
import io.harness.polling.contracts.AMIFilterPayload;
import io.harness.polling.contracts.AMITagPayload;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class AMIArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return AMIArtifactInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .region(pollingPayloadData.getAmiPayload().getRegion())
        .tags(toAMITags(pollingPayloadData.getAmiPayload().getTagsList()))
        .filters(toAMIFilters(pollingPayloadData.getAmiPayload().getFiltersList()))
        .versionRegex(pollingPayloadData.getAmiPayload().getVersionRegex())
        .version(pollingPayloadData.getAmiPayload().getVersion())
        .build();
  }

  public List<AMITag> toAMITags(List<AMITagPayload> tagsPayload) {
    List<AMITag> amiTags = new ArrayList<>();

    for (AMITagPayload amiTagPayload : tagsPayload) {
      String name = amiTagPayload.getName();

      String value = amiTagPayload.getValue();

      AMITag amiTag = AMITag.builder().name(name).value(value).build();

      amiTags.add(amiTag);
    }

    return amiTags;
  }

  public List<AMIFilter> toAMIFilters(List<AMIFilterPayload> filtersPayload) {
    List<AMIFilter> amiFilters = new ArrayList<>();

    for (AMIFilterPayload amiFilterPayload : filtersPayload) {
      String name = amiFilterPayload.getName();

      String value = amiFilterPayload.getValue();

      AMIFilter amiFilter = AMIFilter.builder().name(name).value(value).build();

      amiFilters.add(amiFilter);
    }

    return amiFilters;
  }
}
