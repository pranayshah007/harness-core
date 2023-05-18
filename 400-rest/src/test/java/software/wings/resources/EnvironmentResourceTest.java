/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.pcf.model.PcfConstants.VARS_YML;
import static io.harness.rule.OwnerRule.ANSHUL;

import static software.wings.beans.appmanifest.AppManifestKind.PCF_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.VALUES;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.utils.ResourceTestRule;

import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvironmentResourceTest extends WingsBaseTest {
  private static final EnvironmentService environmentService = mock(EnvironmentService.class);
  private static final ApplicationManifestService appManifestService = mock(ApplicationManifestService.class);
  private static final AuthService authService = mock(AuthService.class);

  private static final String manifestFileId = "manifestFileId";

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new EnvironmentResource(environmentService, authService, appManifestService))
          .type(WingsExceptionMapper.class)
          .build();

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetLocalOverrideManifestFiles() {
    when(appManifestService.getOverrideManifestFilesByEnvId(any(), any()))
        .thenReturn(Arrays.asList(ManifestFile.builder().fileName("vars.yml").build()));

    RestResponse<List<ManifestFile>> restResponse =
        RESOURCES.client()
            .target(format("/environments/%s/manifest-files?appId=%s", ENV_ID, APP_ID))
            .request()
            .get(new GenericType<RestResponse<List<ManifestFile>>>() {});

    assertThat(restResponse.getResource()).isNotEmpty();
    assertThat(restResponse.getResource().get(0).getFileName()).isEqualTo("vars.yml");
    verify(appManifestService).getOverrideManifestFilesByEnvId(APP_ID, ENV_ID);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateValuesForService() {
    ManifestFile manifestFile = ManifestFile.builder().fileName(VARS_YML).build();
    when(environmentService.createValues(any(), any(), any(), any(), any())).thenReturn(manifestFile);

    RestResponse<ManifestFile> restResponse =
        RESOURCES.client()
            .target(
                format("/environments/%s/service/%s/values?appId=%s&kind=%s", ENV_ID, SERVICE_ID, APP_ID, PCF_OVERRIDE))
            .request()
            .post(entity(manifestFile, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ManifestFile>>() {});

    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().getFileName()).isEqualTo(VARS_YML);
    verify(environmentService).createValues(APP_ID, ENV_ID, SERVICE_ID, manifestFile, PCF_OVERRIDE);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateValuesForService() {
    ManifestFile manifestFile = ManifestFile.builder().fileName(VARS_YML).build();
    manifestFile.setUuid(manifestFileId);
    when(environmentService.updateValues(any(), any(), any(), any(), any())).thenReturn(manifestFile);

    RestResponse<ManifestFile> restResponse =
        RESOURCES.client()
            .target(format("/environments/%s/service/%s/values/%s?appId=%s&kind=%s", ENV_ID, SERVICE_ID, manifestFileId,
                APP_ID, PCF_OVERRIDE))
            .request()
            .put(entity(manifestFile, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ManifestFile>>() {});

    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().getFileName()).isEqualTo(VARS_YML);
    verify(environmentService).updateValues(APP_ID, ENV_ID, SERVICE_ID, manifestFile, PCF_OVERRIDE);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateValues() {
    ManifestFile manifestFile = ManifestFile.builder().fileName(VALUES_YAML_KEY).build();
    when(environmentService.createValues(any(), any(), any(), any(), any())).thenReturn(manifestFile);

    RestResponse<ManifestFile> restResponse =
        RESOURCES.client()
            .target(format("/environments/%s/values?appId=%s&kind=%s", ENV_ID, APP_ID, VALUES))
            .request()
            .post(entity(manifestFile, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ManifestFile>>() {});

    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().getFileName()).isEqualTo(VALUES_YAML_KEY);
    verify(environmentService).createValues(APP_ID, ENV_ID, null, manifestFile, VALUES);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateValues() {
    ManifestFile manifestFile = ManifestFile.builder().fileName(VALUES_YAML_KEY).build();
    manifestFile.setUuid(manifestFileId);
    when(environmentService.updateValues(any(), any(), any(), any(), any())).thenReturn(manifestFile);

    RestResponse<ManifestFile> restResponse =
        RESOURCES.client()
            .target(format("/environments/%s/values/%s?appId=%s&kind=%s", ENV_ID, manifestFileId, APP_ID, VALUES))
            .request()
            .put(entity(manifestFile, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ManifestFile>>() {});

    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().getFileName()).isEqualTo(VALUES_YAML_KEY);
    verify(environmentService).updateValues(APP_ID, ENV_ID, null, manifestFile, VALUES);
  }
}
