# see http://typesafehub.github.io/ssl-config/CertificateGeneration.html#server-configuration
export PW=password

# Create a self signed key pair root CA certificate.
keytool -genkeypair -v \
  -alias localhostCA \
  -dname "CN=localhostCA, OU=Local Org, O=Local Company, C=UK" \
  -keystore localhostCA.jks \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -validity 9999

# Export the exampleCA public certificate as exampleca.crt so that it can be used in trust stores.
keytool -export -v \
  -alias localhostCA \
  -file localhostCA.crt \
  -storepass:env PW \
  -keystore localhostCA.jks \
  -rfc

