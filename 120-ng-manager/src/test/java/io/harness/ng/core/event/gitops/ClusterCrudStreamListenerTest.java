package io.harness.ng.core.event.gitops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.protobuf.StringValue;
import com.mongodb.client.result.DeleteResult;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ClusterCrudStreamListenerTest {
  @Mock private ClusterService clusterService;
  @InjectMocks private ClusterCrudStreamListener listener;
  private AutoCloseable closeable;

  @Before
  public void openMocks() throws Exception {
    closeable = MockitoAnnotations.openMocks(this);

    // mock delete result
    doReturn(new DeleteResult() {
      @Override
      public boolean wasAcknowledged() {
        return true;
      }

      @Override
      public long getDeletedCount() {
        return 0;
      }
    })
        .when(clusterService)
        .deleteFromAllEnv(anyString(), anyString(), anyString(), anyString());
  }

  @After
  public void closeMocks() throws Exception {
    closeable.close();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleMessageNoAction() {
    boolean b = listener.handleMessage(messageFor(EntityChangeDTO.newBuilder().build()));

    assertThat(b).isTrue();
    Mockito.verifyNoInteractions(clusterService);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleMessageNoOpActions() {
    Set.of(EventsFrameworkMetadataConstants.CREATE_ACTION, EventsFrameworkMetadataConstants.UPDATE_ACTION,
           EventsFrameworkMetadataConstants.UPSERT_ACTION)
        .forEach(action
            -> assertThat(listener.handleMessage(messageFor(action, EntityChangeDTO.newBuilder().build()))).isTrue());

    Mockito.verifyNoInteractions(clusterService);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleMessageForProjectLevelClusterDeletion() {
    boolean b = listener.handleMessage(messageFor(EventsFrameworkMetadataConstants.DELETE_ACTION,
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue("accountId").build())
            .setOrgIdentifier(StringValue.newBuilder().setValue("orgId").build())
            .setProjectIdentifier(StringValue.newBuilder().setValue("projectId").build())
            .setIdentifier(StringValue.newBuilder().setValue("cluster1").build())
            .build()));

    assertThat(b).isTrue();
    verify(clusterService, times(1)).deleteFromAllEnv("accountId", "orgId", "projectId", "cluster1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleMessageForOrgLevelClusterDeletion() {
    boolean b = listener.handleMessage(messageFor(EventsFrameworkMetadataConstants.DELETE_ACTION,
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue("accountId").build())
            .setOrgIdentifier(StringValue.newBuilder().setValue("orgId").build())
            .setIdentifier(StringValue.newBuilder().setValue("cluster1").build())
            .build()));

    assertThat(b).isTrue();
    verify(clusterService, times(1)).deleteFromAllEnv("accountId", "orgId", "", "cluster1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleMessageForAccountLevelClusterDeletion() {
    boolean b = listener.handleMessage(messageFor(EventsFrameworkMetadataConstants.DELETE_ACTION,
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue("accountId").build())
            .setIdentifier(StringValue.newBuilder().setValue("cluster1").build())
            .build()));

    assertThat(b).isTrue();
    verify(clusterService, times(1)).deleteFromAllEnv("accountId", "", "", "cluster1");
  }

  private Message messageFor(String action, EntityChangeDTO entityChangeDTO) {
    return Message.newBuilder()
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putMetadata(EventsFrameworkMetadataConstants.ENTITY_TYPE,
                            EventsFrameworkMetadataConstants.GITOPS_CLUSTER_ENTITY)
                        .putMetadata(EventsFrameworkMetadataConstants.ACTION, action)
                        .setData(entityChangeDTO.toByteString())
                        .build())
        .build();
  }

  private Message messageFor(EntityChangeDTO entityChangeDTO) {
    return Message.newBuilder()
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putMetadata(EventsFrameworkMetadataConstants.ENTITY_TYPE,
                            EventsFrameworkMetadataConstants.GITOPS_CLUSTER_ENTITY)
                        .setData(entityChangeDTO.toByteString())
                        .build())
        .build();
  }
}