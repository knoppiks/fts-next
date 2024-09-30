package care.smith.fts.tca.consent;

import static care.smith.fts.tca.consent.GicsFhirRequestHelper.nextLink;
import static java.lang.Math.min;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static java.util.stream.Stream.concat;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.util.tca.ConsentFetchRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
class FetchAllConsentsForPersons implements GicsFhirRequestHelper<ConsentFetchRequest> {
  @Override
  public URI buildUri(UriBuilder uri, PagingParams pagingParams) {
    return uri.path("/$allConsentsForPerson").build();
  }

  @Override
  public Map<String, ?> buildBody(ConsentFetchRequest req, PagingParams paging) {
    var patientParams = getPids(req, paging).stream().map(pid -> identifierParam(req, pid));
    var domainParam = Stream.of(Map.of("name", "domain", "valueString", req.domain()));
    return ofEntries(
        entry("resourceType", "Parameters"),
        entry("parameter", concat(domainParam, patientParams).toList()));
  }

  private static Map<String, Object> identifierParam(ConsentFetchRequest req, String pid) {
    return ofEntries(
        entry("name", "personIdentifier"),
        entry("valueIdentifier", Map.of("system", req.patientIdentifierSystem(), "value", pid)));
  }

  /**
   * Get patient IDs from `from` to `from + count`. If `from + count` is greater than pids.size()
   * then return patient IDs from `from` to `pids.size() -1`
   *
   * @param consentRequest
   * @param pagingParams
   * @return List of patient IDs in range
   */
  private static List<String> getPids(
      ConsentFetchRequest consentRequest, PagingParams pagingParams) {
    var end = min(consentRequest.pids().size(), pagingParams.sum());
    return pagingParams.from() < end
        ? consentRequest.pids().subList(pagingParams.from(), end)
        : List.of();
  }

  @Override
  public Bundle processResponse(
      Bundle bundle, ConsentFetchRequest req, UriComponentsBuilder url, PagingParams paging) {
    log.trace("bundle n entries: {}", bundle.getEntry().size());
    return req.pids().size() > paging.sum()
        ? bundle.addLink(nextLink(url, paging, "/api/v2/cd/consented-patients/fetch"))
        : bundle;
  }

  @Override
  public String requestName() {
    return "fetchAllConsentsForPerson";
  }
}
