package care.smith.fts.rda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.rda.DeidentificationProvider;
import com.typesafe.config.ConfigFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirStepFactory
    implements DeidentificationProvider.Factory<DeidentifhirStepConfig> {

  private final WebClient.Builder builder;

  public DeidentifhirStepFactory(WebClient.Builder builder) {
    this.builder = builder;
  }

  @Override
  public Class<DeidentifhirStepConfig> getConfigType() {
    return DeidentifhirStepConfig.class;
  }

  @Override
  public DeidentificationProvider create(
      DeidentificationProvider.Config commonConfig, DeidentifhirStepConfig implConfig) {

    var httpClient = implConfig.tca().server().createClient(builder);
    var config = ConfigFactory.parseFile(requireNonNull(implConfig.deidentifhirConfig()));
    return new DeidentifhirStep(
        config, httpClient, implConfig.tca().domain(), implConfig.dateShift());
  }
}
