/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.filter;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.filter.FilterConstants.DELEGATE_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(DELEGATE_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("DelegateFilterProperties")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Properties to filter delegates")
@OwnedBy(DEL)
public class DelegateFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "Filter on delegate connectivity") private DelegateInstanceConnectivityStatus status;
  @Schema(description = "Filter on delegate description") private String description;
  @Schema(description = "Filter on delegate name") private String delegateName;
  @Schema(description = "Filter on delegate type") private String delegateType;
  @Schema(description = "Filter on delegate group id") private String delegateGroupIdentifier;
  @Schema(description = "Filter on delegate tags") private Set<String> delegateTags;
  @Schema(description = "Filter on delegate instance status") private DelegateInstanceFilter delegateInstanceFilter;

  @Override
  public FilterType getFilterType() {
    return FilterType.DELEGATE;
  }
}
