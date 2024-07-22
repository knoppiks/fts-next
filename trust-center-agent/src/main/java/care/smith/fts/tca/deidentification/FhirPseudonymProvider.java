package care.smith.fts.tca.deidentification;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.util.error.UnknownDomainException;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Slf4j
@Component
public class FhirPseudonymProvider implements PseudonymProvider {
  private static final String ALLOWED_PSEUDONYM_CHARS =
      "0123456789abcdefghijklmnopqrstuvwxyz-_ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private final WebClient httpClient;
  private final PseudonymizationConfiguration configuration;
  private final JedisPool jedisPool;
  private final RandomGenerator randomGenerator;

  public FhirPseudonymProvider(
      @Qualifier("gpasFhirHttpClient") WebClient httpClient,
      JedisPool jedisPool,
      PseudonymizationConfiguration configuration,
      RandomGenerator randomGenerator) {
    this.httpClient = httpClient;
    this.configuration = configuration;
    this.jedisPool = jedisPool;
    this.randomGenerator = randomGenerator;
  }

  /**
   * For all provided IDs fetch the id:pid pairs from gPAS. Then create TransportIDs (id:tid pairs).
   * Store tid:pid in the key-value-store.
   *
   * @param ids the IDs to pseudonymize
   * @param domain the domain used in gPAS
   * @return the TransportIDs
   */
  @Override
  public Mono<Map<String, String>> retrieveTransportIds(Set<String> ids, String domain) {
    var transportIds = new HashMap<String, String>();
    ids.forEach(id -> transportIds.put(id, getUniqueTransportId()));

    return fetchOrCreatePseudonyms(domain, ids)
        .map(
            idPseudonyms -> {
              log.trace("Storing pseudonyms {} for {}", idPseudonyms, ids);
              try (Jedis jedis = jedisPool.getResource()) {
                ids.forEach(
                    id -> {
                      var transportId = transportIds.get(id);
                      var kid = "tid:" + transportId;
                      String pid = idPseudonyms.get(id);
                      if (jedis.set(kid, pid, new SetParams().nx()).equals("OK")) {
                        jedis.expire(kid, configuration.getTransportIdTTLinSeconds());
                      } else {
                        throw new IllegalStateException(
                            "Could not put {kid}-{pid} into key-value-store");
                      }
                    });
              }

              return transportIds;
            });
  }

  /** Generate a random transport ID and make sure it does not yet exist in the key-value-store. */
  private String getUniqueTransportId() {
    var tid =
        randomGenerator
            .ints(9, 0, ALLOWED_PSEUDONYM_CHARS.length())
            .mapToObj(ALLOWED_PSEUDONYM_CHARS::charAt)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();
    try (Jedis jedis = jedisPool.getResource()) {
      if (jedis.get("tid:" + tid) != null) {
        return getUniqueTransportId();
      }
    }
    return tid;
  }

  private Mono<Map<String, String>> fetchOrCreatePseudonyms(String domain, Set<String> ids) {
    var idParams =
        Stream.concat(
            Stream.of(Map.of("name", "target", "valueString", domain)),
            ids.stream().map(id -> Map.of("name", "original", "valueString", id)));
    var params = Map.of("resourceType", "Parameters", "parameter", idParams.toList());

    log.trace(
        "fetchOrCreatePseudonyms for domain: %s and ids: %s".formatted(domain, ids.toString()));

    return httpClient
        .post()
        .uri("/$pseudonymizeAllowCreate")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(params)
        .headers(h -> h.setAccept(List.of(APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(
            r -> r.equals(HttpStatus.BAD_REQUEST), FhirPseudonymProvider::handleGpasBadRequest)
        .bodyToMono(GpasParameterResponse.class)
        .doOnNext(r -> log.trace("$pseudonymize response: {}", r))
        .map(GpasParameterResponse::getMappedID);
  }

  private static Mono<Throwable> handleGpasBadRequest(ClientResponse r) {
    return r.bodyToMono(OperationOutcome.class)
        .flatMap(
            b -> {
              var diagnostics = b.getIssueFirstRep().getDiagnostics();
              log.error("Bad Request: {}", diagnostics);
              if (diagnostics != null && diagnostics.startsWith("Unknown domain")) {
                return Mono.error(new UnknownDomainException(diagnostics));
              } else {
                return Mono.error(new UnknownError());
              }
            });
  }

  @Override
  public Mono<Map<String, String>> fetchPseudonymizedIds(Set<String> ids) {
    if (!ids.isEmpty()) {
      Map<String, String> pseudonyms = new HashMap<>();
      try (Jedis jedis = jedisPool.getResource()) {
        ids.forEach(
            id -> {
              var pseudonymId = jedis.get("tid:" + id);
              pseudonyms.put(id, pseudonymId);
            });
      }
      return Mono.just(pseudonyms);
    } else {
      return Mono.empty();
    }
  }
}
