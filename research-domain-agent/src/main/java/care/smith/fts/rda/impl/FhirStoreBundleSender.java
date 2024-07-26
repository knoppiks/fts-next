package care.smith.fts.rda.impl;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;

import care.smith.fts.api.rda.BundleSender;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
final class FhirStoreBundleSender implements BundleSender {
  private final WebClient client;

  public FhirStoreBundleSender(WebClient client) {
    this.client = client;
  }

  @Override
  public Mono<Result> send(Bundle bundle) {
    log.trace("Sending bundle");
    return client
        .post()
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(toTransactionBundle(bundle))
        .retrieve()
        .toBodilessEntity()
        .retryWhen(defaultRetryStrategy())
        .doOnNext(res -> log.trace("Response received: {}", res))
        .doOnError(err -> log.debug("Error received", err))
        .map(b -> new Result());
  }

  private static Bundle toTransactionBundle(Bundle bundle) {
    Bundle transactionBundle = new Bundle();
    resourceStream(bundle)
        .map(FhirStoreBundleSender::createPutEntry)
        .forEach(transactionBundle::addEntry);
    transactionBundle.setType(TRANSACTION);
    return transactionBundle;
  }

  private static BundleEntryComponent createPutEntry(Resource r) {
    var value =
        new Bundle.BundleEntryRequestComponent()
            .setMethod(Bundle.HTTPVerb.PUT)
            .setUrl("%s/%s".formatted(r.getResourceType().name(), r.getIdPart()));
    return new BundleEntryComponent().setRequest(value).setResource(r);
  }
}
