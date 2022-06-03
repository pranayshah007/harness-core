package io.harness.shell;

import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
public class FileBasedSshScriptExecutorHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCheckAck() throws IOException {
    InputStream inputStreamMock = Mockito.mock(InputStream.class);
    Consumer<String> loggerMock = Mockito.mock(Consumer.class);

    doReturn(0).when(inputStreamMock).read();
    assertThat(FileBasedSshScriptExecutorHelper.checkAck(inputStreamMock, loggerMock)).isEqualTo(0);

    doReturn(-1).when(inputStreamMock).read();
    assertThat(FileBasedSshScriptExecutorHelper.checkAck(inputStreamMock, loggerMock)).isEqualTo(-1);

    Mockito.reset(inputStreamMock, loggerMock);

    List<Integer> reads = "Welcome!\n".chars().boxed().collect(Collectors.toList());
    when(inputStreamMock.read()).thenReturn((int) '#', reads.toArray(new Integer[0]));
    when(inputStreamMock.available()).thenReturn(10);
    assertThat(FileBasedSshScriptExecutorHelper.checkAck(inputStreamMock, loggerMock)).isEqualTo(0);

    Mockito.reset(inputStreamMock, loggerMock);

    when(inputStreamMock.read()).thenReturn(1, reads.toArray(new Integer[0]));
    when(inputStreamMock.available()).thenReturn(10);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    assertThat(FileBasedSshScriptExecutorHelper.checkAck(inputStreamMock, loggerMock)).isEqualTo(1);
    verify(loggerMock).accept(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo("Welcome!\n");

    when(inputStreamMock.read()).thenReturn(2, reads.toArray(new Integer[0]));
    when(inputStreamMock.available()).thenReturn(10);
    assertThat(FileBasedSshScriptExecutorHelper.checkAck(inputStreamMock, loggerMock)).isEqualTo(1);
  }
}