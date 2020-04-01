package io.harness;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static org.assertj.core.api.Assertions.assertThat;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.graphql.schema.type.QLService.QLServiceKeys;
import static software.wings.graphql.schema.type.QLTag.QLTagKeys;

import com.google.inject.Inject;

import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.rule.Owner;
import io.harness.testframework.graphql.QLTestObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.beans.Application;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceBuilder;
import software.wings.graphql.schema.type.QLServiceConnection;
import software.wings.graphql.schema.type.QLUser.QLUserKeys;
import software.wings.service.intfc.HarnessTagService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class ServiceTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private HarnessTagService harnessTagService;

  @Test
  @Owner(developers = GEORGE)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryService() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());

    final Service service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    assertThat(service).isNotNull();
    {
      String query = $GQL(/*
{
  service(serviceId: "%s") {
    id
    name
    description
    artifactType
    deploymentType
    createdAt
    createdBy {
      id
      name
      email
    }
  }
}*/ service.getUuid());

      QLTestObject qlService = qlExecute(query, service.getAccountId());
      assertThat(qlService.get(QLServiceKeys.id)).isEqualTo(service.getUuid());
      assertThat(qlService.get(QLServiceKeys.name)).isEqualTo(service.getName());
      assertThat(qlService.get(QLServiceKeys.description)).isEqualTo(service.getDescription());
      assertThat(qlService.get(QLServiceKeys.artifactType)).isEqualTo(service.getArtifactType().name());
      assertThat(qlService.get(QLServiceKeys.deploymentType)).isEqualTo(service.getDeploymentType().name());
      assertThat(qlService.get(QLServiceKeys.createdAt)).isEqualTo(service.getCreatedAt());
      assertThat(qlService.sub(QLServiceKeys.createdBy).get(QLUserKeys.id)).isEqualTo(service.getCreatedBy().getUuid());
      assertThat(qlService.sub(QLServiceKeys.createdBy).get(QLUserKeys.name))
          .isEqualTo(service.getCreatedBy().getName());
      assertThat(qlService.sub(QLServiceKeys.createdBy).get(QLUserKeys.email))
          .isEqualTo(service.getCreatedBy().getEmail());
    }

    {
      String query = $GQL(/*
{
  service(serviceId: "%s") {
    id
    tags {
      name
      value
    }
  }
}*/ service.getUuid());
      attachTagToService(service);
      QLTestObject qlService = qlExecute(query, service.getAccountId());
      assertThat(qlService.get(QLServiceKeys.id)).isEqualTo(service.getUuid());
      Map<String, String> tagsMap = (LinkedHashMap) (((ArrayList) qlService.get("tags")).get(0));
      assertThat(tagsMap.get(QLTagKeys.name)).isEqualTo("color");
      assertThat(tagsMap.get(QLTagKeys.value)).isEqualTo("red");
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryServices() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();

    final Application application = applicationGenerator.ensureApplication(
        seed, owners, Application.Builder.anApplication().name("Service App").build());

    final ServiceBuilder serviceBuilder = Service.builder().appId(application.getUuid());

    final Service service1 = serviceGenerator.ensureService(
        seed, owners, serviceBuilder.name("Service1").uuid(UUIDGenerator.generateUuid()).build());
    final Service service2 = serviceGenerator.ensureService(
        seed, owners, serviceBuilder.name("Service2").uuid(UUIDGenerator.generateUuid()).build());
    final Service service3 = serviceGenerator.ensureService(
        seed, owners, serviceBuilder.name("Service3").uuid(UUIDGenerator.generateUuid()).build());

    {
      String query = $GQL(/*
{
  services(filters:[{application:{operator:EQUALS,values:["%s"]}}], limit: 2) {
    nodes {
      id
    }
  }
}*/ application.getUuid());

      QLServiceConnection serviceConnection = qlExecute(QLServiceConnection.class, query, application.getAccountId());
      assertThat(serviceConnection.getNodes().size()).isEqualTo(2);
      assertThat(serviceConnection.getNodes().get(0).getId()).isEqualTo(service3.getUuid());
      assertThat(serviceConnection.getNodes().get(1).getId()).isEqualTo(service2.getUuid());
    }

    {
      String query = $GQL(/*
{
  services(filters:[{application:{operator:EQUALS,values:["%s"]}}] limit: 2 offset: 1) {
    nodes {
      id
    }
  }
}*/ application.getUuid());

      QLServiceConnection serviceConnection = qlExecute(QLServiceConnection.class, query, application.getAccountId());
      assertThat(serviceConnection.getNodes().size()).isEqualTo(2);

      assertThat(serviceConnection.getNodes().get(0).getId()).isEqualTo(service2.getUuid());
      assertThat(serviceConnection.getNodes().get(1).getId()).isEqualTo(service1.getUuid());
    }

    {
      String query = $GQL(/*
{
  application(applicationId: "%s") {
    services(limit: 5) {
      nodes {
        id
      }
    }
  }
}*/ application.getUuid());

      final QLTestObject qlTestObject = qlExecute(query, application.getAccountId());
      assertThat(qlTestObject.getMap().size()).isEqualTo(1);
    }

    {
      String query = $GQL(/*
{
  services(filters:[{service:{operator:IN,values:["%s"]}}] limit: 3) {
    nodes {
      id
      tags {
        name
        value
      }
    }
  }
}*/ service1.getUuid());

      attachTagToService(service1);
      QLTestObject serviceConnection = qlExecute(query, application.getAccountId());
      Map<String, Object> serviceMap = (LinkedHashMap) (((ArrayList) serviceConnection.get("nodes")).get(0));
      assertThat(serviceMap.get(QLServiceKeys.id)).isEqualTo(service1.getUuid());
      Map<String, String> tagsMap = (LinkedHashMap) (((ArrayList) serviceMap.get("tags")).get(0));
      assertThat(tagsMap.get(QLTagKeys.name)).isEqualTo("color");
      assertThat(tagsMap.get(QLTagKeys.value)).isEqualTo("red");
    }
  }

  private void attachTagToService(Service service) {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(service.getAccountId())
                                    .appId(service.getAppId())
                                    .entityId(service.getUuid())
                                    .entityType(SERVICE)
                                    .key("color")
                                    .value("red")
                                    .build());
  }
}
