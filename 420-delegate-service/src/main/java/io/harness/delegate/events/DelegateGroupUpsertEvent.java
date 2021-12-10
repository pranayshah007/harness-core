package io.harness.delegate.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeEnum;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.ng.core.Resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.DEL)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DelegateGroupUpsertEvent extends AbstractDelegateConfigurationEvent {
  private DelegateSetupDetails delegateSetupDetails;
  private String delegateGroupId;

  @Override
  public Resource getResource() {
    return Resource.builder().identifier(delegateGroupId).type(Resource.Type.DELEGATE).build();
  }

  @Override
  public String getEventType() {
    return "DelegateGroupUpsertEvent";
  }
}
