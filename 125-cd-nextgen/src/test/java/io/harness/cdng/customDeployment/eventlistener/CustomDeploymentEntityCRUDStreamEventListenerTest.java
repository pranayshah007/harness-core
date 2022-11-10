/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment.eventlistener;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.rule.OwnerRule.ANIL;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CustomDeploymentEntityCRUDStreamEventListenerTest extends CategoryTest {
  @Mock CustomDeploymentEntityCRUDEventHandler deploymentTemplateEntityCRUDEventHandler;
  @InjectMocks CustomDeploymentEntityCRUDStreamEventListener deploymentEntityEventListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleMessageDelete() {
    ByteString bytes = EntityChangeDTO.newBuilder()
                           .setIdentifier(StringValue.newBuilder().setValue("deploymentTemplateId"))
                           .setAccountIdentifier(StringValue.newBuilder().setValue("accountIdentifier"))
                           .build()
                           .toByteString();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, "TEMPLATE")
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(bytes)
                                          .build())
                          .build();
    assertTrue(deploymentEntityEventListener.handleMessage(message));
    verify(deploymentTemplateEntityCRUDEventHandler, times(0)).updateInfraAsObsolete(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleMessageCreate() {
    ByteString bytes = EntityChangeDTO.newBuilder()
                           .setIdentifier(StringValue.newBuilder().setValue("deploymentTemplateId"))
                           .setAccountIdentifier(StringValue.newBuilder().setValue("accountIdentifier"))
                           .build()
                           .toByteString();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, "TEMPLATE")
                                          .putMetadata(ACTION, CREATE_ACTION)
                                          .setData(bytes)
                                          .build())
                          .build();
    assertTrue(deploymentEntityEventListener.handleMessage(message));
    verify(deploymentTemplateEntityCRUDEventHandler, times(0)).updateInfraAsObsolete(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleMessageUpdate() {
    ByteString bytes = EntityChangeDTO.newBuilder()
                           .setIdentifier(StringValue.newBuilder().setValue("deploymentTemplateId"))
                           .setProjectIdentifier(StringValue.newBuilder().setValue("projectId"))
                           .setOrgIdentifier(StringValue.newBuilder().setValue("orgId"))
                           .setAccountIdentifier(StringValue.newBuilder().setValue("accountIdentifier"))
                           .putMetadata("versionLabel", "1")
                           .putMetadata("templateType", TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.toString())
                           .build()
                           .toByteString();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, "TEMPLATE")
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .setData(bytes)
                                          .build())
                          .build();
    doReturn(true)
        .when(deploymentTemplateEntityCRUDEventHandler)
        .updateInfraAsObsolete(
            eq("accountIdentifier"), eq("orgId"), eq("projectId"), eq("deploymentTemplateId"), eq("1"));
    assertTrue(deploymentEntityEventListener.handleMessage(message));
    verify(deploymentTemplateEntityCRUDEventHandler, times(1))
        .updateInfraAsObsolete(
            eq("accountIdentifier"), eq("orgId"), eq("projectId"), eq("deploymentTemplateId"), eq("1"));

    doThrow(InvalidRequestException.class)
        .when(deploymentTemplateEntityCRUDEventHandler)
        .updateInfraAsObsolete(
            eq("accountIdentifier"), eq("orgId"), eq("projectId"), eq("deploymentTemplateId"), eq("1"));
    assertTrue(deploymentEntityEventListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleMessageUpdateStableVersion() {
    ByteString bytes = EntityChangeDTO.newBuilder()
                           .setIdentifier(StringValue.newBuilder().setValue("deploymentTemplateId"))
                           .setProjectIdentifier(StringValue.newBuilder().setValue("projectId"))
                           .setOrgIdentifier(StringValue.newBuilder().setValue("orgId"))
                           .setAccountIdentifier(StringValue.newBuilder().setValue("accountIdentifier"))
                           .putMetadata("versionLabel", "1")
                           .putMetadata("isStable", "true")
                           .putMetadata("templateType", TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.toString())
                           .build()
                           .toByteString();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, "TEMPLATE")
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .setData(bytes)
                                          .build())
                          .build();
    doReturn(true)
        .when(deploymentTemplateEntityCRUDEventHandler)
        .updateInfraAsObsolete(
            eq("accountIdentifier"), eq("orgId"), eq("projectId"), eq("deploymentTemplateId"), eq("1"));
    assertTrue(deploymentEntityEventListener.handleMessage(message));
    verify(deploymentTemplateEntityCRUDEventHandler, times(1))
        .updateInfraAsObsolete(
            eq("accountIdentifier"), eq("orgId"), eq("projectId"), eq("deploymentTemplateId"), eq("1"));

    verify(deploymentTemplateEntityCRUDEventHandler, times(1))
        .updateInfraAsObsolete(
            eq("accountIdentifier"), eq("orgId"), eq("projectId"), eq("deploymentTemplateId"), eq(null));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleMessageUpdateError() {
    ByteString bytes = EntityChangeDTO.newBuilder()
                           .setIdentifier(StringValue.newBuilder().setValue("deploymentTemplateId"))
                           .setProjectIdentifier(StringValue.newBuilder().setValue("projectId"))
                           .setOrgIdentifier(StringValue.newBuilder().setValue("orgId"))
                           .setAccountIdentifier(StringValue.newBuilder().setValue("accountIdentifier"))
                           .putMetadata("versionLabel", "1")
                           .putMetadata("isStable", "true")
                           .putMetadata("templateType", TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.toString())
                           .build()
                           .toByteString();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .setData(bytes)
                                          .build())
                          .build();

    assertTrue(deploymentEntityEventListener.handleMessage(null));
    assertTrue(deploymentEntityEventListener.handleMessage(message));
  }
}
