package care.smith.fts.tca;

import static java.time.Duration.ofSeconds;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.WebClientDefaults;
import care.smith.fts.util.WebClientFhirCodec;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({WebClientDefaults.class, WebClientFhirCodec.class})
public class TrustCenterAgent {

  public static void main(String... args) {
    SpringApplication.run(TrustCenterAgent.class, args);
  }

  @Bean
  public HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }
}
