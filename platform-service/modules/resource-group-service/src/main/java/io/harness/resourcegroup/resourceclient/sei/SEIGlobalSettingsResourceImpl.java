package io.harness.resourcegroup.resourceclient.sei;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.v2.model.AttributeFilter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static org.apache.commons.lang3.StringUtils.stripToNull;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject}))
@Slf4j
public class SEIGlobalSettingsResourceImpl  implements Resource {
    @Override
    public String getType() {
        return "SEI_CONFIGURATION_SETTINGS";
    }

    @Override
    public Set<ScopeLevel> getValidScopeLevels() {
        return EnumSet.of(ScopeLevel.ACCOUNT);
    }

    @Override
    public Optional<String> getEventFrameworkEntityType() {
        return Optional.of(EventsFrameworkMetadataConstants.CONFIGURATION_SETTINGS);
    }

    @Override
    public ResourceInfo getResourceInfoFromEvent(Message message) {
        EntityChangeDTO entityChangeDTO = null;
        try {
            entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
            log.error("Exception in unpacking EntityChangeDTO for key {}", message.getId(), e);
        }
        if (Objects.isNull(entityChangeDTO)) {
            return null;
        }

        return ResourceInfo.builder()
                .accountIdentifier(stripToNull(entityChangeDTO.getAccountIdentifier().getValue()))
                .resourceType(getType())
                .resourceIdentifier(entityChangeDTO.getIdentifier().getValue())
                .build();
    }

    @Override
    public List<Boolean> validate(List<String> resourceIds, Scope scope) {
        return null;
    }

    @Override
    public Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind() {
        return ImmutableMap.of(ScopeLevel.ACCOUNT, EnumSet.of(BY_RESOURCE_TYPE, BY_RESOURCE_IDENTIFIER));
    }
    @Override
    public boolean isValidAttributeFilter(AttributeFilter attributeFilter) {
        return false;
    }
}
