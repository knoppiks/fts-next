package care.smith.fts.rda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.test.FhirGenerators;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class DeidentifierIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockDeidentifier.isDown();
    startProcessAndExpectError(Duration.ofSeconds(1));
  }

  @Test
  void tcaTimeout() {
    mockDeidentifier.hasTimeout();
    startProcessAndExpectError(Duration.ofSeconds(10));
  }

  @Test
  void tcaReturnsWrongContentType() {
    mockDeidentifier.returnsWrongContentType();
    startProcessAndExpectError(Duration.ofMillis(200));
  }

  @Test
  void tcaFirstRequestFails() throws IOException {
    mockDeidentifier.success(List.of(500));
    mockBundleSender.success();

    var transportBundle = FhirGenerators.transportBundle().generateResource();

    log.info("Start process with transport bundle of size {}", transportBundle.getEntry().size());

    startProcess(
        transportBundle,
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.receivedResources()).isEqualTo(366);
          assertThat(r.sentResources()).isEqualTo(1);
        });
  }
}
