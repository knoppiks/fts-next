package care.smith.fts.rda.rest;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.google.common.base.Predicates.and;
import static java.util.function.Predicate.not;
import static reactor.core.publisher.Mono.error;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcess;
import care.smith.fts.rda.TransferProcessRunner;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v2")
public class TransferProcessController {

  private final TransferProcessRunner processRunner;
  private final List<TransferProcess> processes;

  public TransferProcessController(TransferProcessRunner runner, List<TransferProcess> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(value = "/{project}/patient", consumes = "application/fhir+json")
  Mono<TransferProcessRunner.Result> start(
      @PathVariable("project") String project, @RequestBody Mono<Bundle> data) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process.get());
      return processRunner
          .run(process.get(), data.map(this::fromTransportBundle))
          .doOnNext(result1 -> log.debug("Process run finished: {}", result1))
          .doOnCancel(() -> log.warn("Process run cancelled"))
          .doOnError(err -> log.debug("Process run errored", err));
    } else {
      return error(new IllegalStateException("Project %s could not be found".formatted(project)));
    }
  }

  private TransportBundle fromTransportBundle(Bundle bundle) {
    var bundleWithoutParameters =
        resourceStream(bundle)
            .filter(not(and(Parameters.class::isInstance, p -> p.getId().equals("transport-ids"))))
            .collect(toBundle());
    var transportIds =
        resourceStream(bundle)
            .filter(Parameters.class::isInstance)
            .filter(p -> p.getId().equals("transport-ids"))
            .map(Parameters.class::cast)
            .findFirst()
            .map(TransferProcessController::extractTransportIds)
            .orElse(Set.of());
    return new TransportBundle(bundleWithoutParameters, transportIds);
  }

  private static Set<String> extractTransportIds(Parameters resource) {
    return resource.getParameters("transport-id").stream()
        .map(Parameters.ParametersParameterComponent::getValue)
        .map(StringType.class::cast)
        .map(PrimitiveType::getValue)
        .collect(Collectors.toSet());
  }

  private Optional<TransferProcess> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }
}
