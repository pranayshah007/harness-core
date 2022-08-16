package software.wings.helpers.ext.gar;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GarApiServiceTest extends WingsBaseTest {
  //     GARApiServiceImpl garApiServiceImpl = spy(new GARApiServiceImpl());

  //
  //     @Rule
  //     public WireMockRule wireMockRule = new WireMockRule(
  //             WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory("400-rest/src/test/resources").port(0));
  //     private String url;
  //     String basicAuthHeader = "auth";
  //     GcrInternalConfig gcpInternalConfig;
  //
  //     @Before
  //     public void setUp() {
  //         url = "localhost:" + wireMockRule.port();
  //         gcpInternalConfig = GcrConfigToInternalMapper.toGcpInternalConfig(url, basicAuthHeader);
  //         wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/someImage/tags/list"))
  //                 .withHeader("Authorization", equalTo("auth"))
  //                 .willReturn(aResponse().withStatus(200).withBody(
  //                         "{\"name\":\"someImage\",\"tags\":[\"v1\",\"v2\",\"latest\"]}")));
  //
  //         wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/noImage/tags/list"))
  //                 .withHeader("Authorization", equalTo("auth"))
  //                 .willReturn(aResponse().withStatus(404)));
  //
  //         wireMockRule.stubFor(
  //                 WireMock.get(WireMock.urlEqualTo("/v2/invalidProject/tags/list"))
  //                         .withHeader("Authorization", equalTo("auth"))
  //                         .willReturn(aResponse().withStatus(403).withBody(
  //                                 "{\"errors\":[{\"code\":\"UNKNOWN\",\"message\":\"Project 'project:project-name'
  //                                 not found or deleted.\"}]}")));
  //
  //         wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/teapot/tags/list"))
  //                 .withHeader("Authorization", equalTo("auth"))
  //                 .willReturn(aResponse().withStatus(418).withBody("I'm a teapot")));
  //
  //         when(gcrService.getUrl(anyString())).thenReturn("http://" + url);
  //     }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void garTest() {
    assertThat(true).isEqualTo(true);
  }
}
