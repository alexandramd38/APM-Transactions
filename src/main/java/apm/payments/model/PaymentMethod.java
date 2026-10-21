package apm.payments.model;

import java.util.Set;

public enum PaymentMethod {
  PAYPAL(Set.of("EUR", "USD", "GBP", "PLN", "SEK", "NOK", "DKK", "CHF"), Set.of()),
  IDEAL(Set.of("EUR"), Set.of("NL")),
  KLARNA(
      Set.of("EUR", "SEK", "NOK", "DKK", "GBP", "USD"),
      Set.of("DE", "AT", "NL", "BE", "FI", "SE", "NO", "DK", "GB", "US")),
  SOFORT(Set.of("EUR"), Set.of("DE", "AT", "BE", "NL", "IT", "ES")),
  BANCONTACT(Set.of("EUR"), Set.of("BE")),
  GIROPAY(Set.of("EUR"), Set.of("DE")),
  BLIK(Set.of("PLN"), Set.of("PL")),
  ALIPAY(Set.of("CNY", "USD", "EUR", "GBP"), Set.of()),
  WECHAT_PAY(Set.of("CNY", "USD", "EUR", "GBP"), Set.of());

  private final Set<String> supportedCurrencies;
  private final Set<String> supportedCountries;

  PaymentMethod(Set<String> supportedCurrencies, Set<String> supportedCountries) {
    this.supportedCurrencies = supportedCurrencies;
    this.supportedCountries = supportedCountries;
  }

  public boolean supportsCurrency(String currency) {
    return supportedCurrencies.contains(currency);
  }

  /** An empty set of supported countries means the method is available in every country. */
  public boolean supportsCountry(String countryCode) {
    return supportedCountries.isEmpty() || supportedCountries.contains(countryCode);
  }

  public Set<String> getSupportedCurrencies() {
    return supportedCurrencies;
  }

  public Set<String> getSupportedCountries() {
    return supportedCountries;
  }
}
