package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.DeidentificationProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, Run> runs = new HashMap<>();

  @Override
  public String run(TransferProcess process) {
    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);
    Run run = new Run(process);
    run.execute();
    runs.put(processId, run);
    return processId;
  }

  @Override
  public Mono<State> state(String processId) {
    Run run = runs.get(processId);
    if (run != null) {
      return Mono.just(run.state(processId));
    } else {
      return Mono.error(new IllegalArgumentException());
    }
  }

  public static class Run {

    private final CohortSelector cohortSelector;
    private final DataSelector dataSelector;
    private final DeidentificationProvider deidentificationProvider;
    private final AtomicLong skippedPatients;
    private final BundleSender bundleSender;
    private final AtomicLong sentBundles;
    private final AtomicReference<Status> status;

    public Run(TransferProcess process) {
      cohortSelector = process.cohortSelector();
      dataSelector = process.dataSelector();
      deidentificationProvider = process.deidentificationProvider();
      bundleSender = process.bundleSender();

      skippedPatients = new AtomicLong();
      sentBundles = new AtomicLong();
      status = new AtomicReference<>(Status.QUEUED);
    }

    public void execute() {
      status.set(Status.RUNNING);
      cohortSelector
          .selectCohort()
          .doOnError(e -> log.error(e.getMessage()))
          .doOnError(e -> status.set(Status.ERROR))
          .flatMap(this::executePatient)
          .doOnComplete(() -> status.set(Status.COMPLETED))
          .onErrorComplete()
          .subscribe();
    }

    private Mono<Result> executePatient(ConsentedPatient patient) {
      return dataSelector
          .select(patient)
          .map(b -> new ConsentedPatientBundle(b, patient))
          .transform(deidentificationProvider::deidentify)
          .as(bundleSender::send)
          .doOnNext(r -> sentBundles.getAndAdd(r.bundleCount()))
          .doOnError(e -> skippedPatients.incrementAndGet())
          .doOnError(e -> log.error("Skipping patient: {}", e.getMessage()))
          .onErrorResume(e -> Mono.just(new Result(0)));
    }

    public State state(String processId) {
      return new State(processId, status.get(), sentBundles.get(), skippedPatients.get());
    }
  }
}
