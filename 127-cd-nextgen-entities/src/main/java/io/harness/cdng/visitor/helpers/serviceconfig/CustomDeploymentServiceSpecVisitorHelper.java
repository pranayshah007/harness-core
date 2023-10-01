/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.serviceconfig;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.CustomDeploymentServiceSpec;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import com.google.protobuf.StringValue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentServiceSpecVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return CustomDeploymentServiceSpec.builder().build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // nothing to validate
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    if (!(object instanceof CustomDeploymentServiceSpec)) {
      throw new InvalidRequestException(String.format(
          "Object of class %s does not implement CustomDeploymentServiceSpec, and hence can't have CustomDeploymentServiceSpecVisitorHelper as its visitor helper",
          object.getClass().toString()));
    }
    CustomDeploymentServiceSpec serviceSpec = (CustomDeploymentServiceSpec) object;
    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (!isNull(serviceSpec.getCustomDeploymentRef())) {
      String versionLabel = serviceSpec.getCustomDeploymentRef().getVersionLabel();
      versionLabel = isEmpty(versionLabel) ? "__STABLE__" : versionLabel;
      TemplateReferenceProtoDTO.Builder templateReferenceProtoDTO =
          TemplateReferenceProtoDTO.newBuilder()
              .setAccountIdentifier(StringValue.of(accountIdentifier))
              .setVersionLabel(StringValue.of(versionLabel));
      String templateRef = serviceSpec.getCustomDeploymentRef().getTemplateRef();
      if (templateRef.contains("account.")) {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ACCOUNT)
            .setIdentifier(StringValue.of(templateRef.replace("account.", "")));
      } else if (templateRef.contains("org.")) {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ORG)
            .setOrgIdentifier(StringValue.of(orgIdentifier))
            .setIdentifier(StringValue.of(templateRef.replace("org.", "")));
      } else {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.PROJECT)
            .setOrgIdentifier(StringValue.of(orgIdentifier))
            .setProjectIdentifier(StringValue.of(projectIdentifier))
            .setIdentifier(StringValue.of(templateRef));
      }
      result.add(EntityDetailProtoDTO.newBuilder()
                     .setType(TEMPLATE)
                     .setTemplateRef(templateReferenceProtoDTO.build())
                     .build());
    }
    return result;
  }
}
