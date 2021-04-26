<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/conjure-java-runtime"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

[![CircleCI Build Status](https://circleci.com/gh/palantir/conjure-java-runtime/tree/develop.svg?style=shield)](https://circleci.com/gh/palantir/conjure-java-runtime)
[![Download](https://api.bintray.com/packages/palantir/releases/conjure-java-runtime/images/download.svg) ](https://bintray.com/palantir/releases/conjure-java-runtime/_latestVersion)

# Conjure Java Runtime (formerly http-remoting)
This repository provides an opinionated set of libraries for defining and creating RESTish/RPC servers and clients based
on [Feign](https://github.com/OpenFeign/feign) as a client and
[Dropwizard](http://www.dropwizard.io/)/[Jersey](https://jersey.java.net/) with [JAX-RS](https://jax-rs-spec.java.net/)
service definitions as a server. Refer to the [API Contract](#api-contract) section for details on the contract between
clients and servers. This library requires Java 8.

Core libraries:
- conjure-java-jaxrs-client: Clients for JAX-RS-defined service interfaces
- conjure-java-jersey-server: Configuration library for Dropwizard/Jersey servers
- [conjure-java-runtime-api](https://github.com/palantir/conjure-java-runtime-api): API classes for service configuration, tracing, and error propagation

# Usage

Maven artifacts are published to JCenter. Example Gradle dependency configuration:

```groovy
repositories {
  jcenter()
}

dependencies {
  compile "com.palantir.conjure.java.runtime:conjure-java-jaxrs-client:$version"
  compile "com.palantir.conjure.java.runtime:conjure-java-jersey-server:$version"
}
```


## conjure-java-jaxrs-client
Provides the `JaxRsClient` factory for creating Feign-based clients for JAX-RS APIs. SSL configuration is mandatory for
all clients, plain-text HTTP is not supported. Example:

```java
SslConfiguration sslConfig = SslConfiguration.of(Paths.get("path/to/trustStore"));
UserAgent userAgent = UserAgent.of(UserAgent.Agent.of("my-user-agent", "1.0.0"));
ClientConfiguration config = ClientConfigurations.of(
        ImmutableList.copyOf("https://url-to-server:6789"),
        SslSocketFactories.createSslSocketFactory(sslConfig),
        SslSocketFactories.createX509TrustManager(sslConfig));
HostMetricsRegistry hostMetricsRegistry = new HostMetricsRegistry();  // can call .getMetrics() and then collect them to a central metrics repository
MyService service = JaxRsClient.create(MyService.class, userAgent, hostMetricsRegistry, config);
```

The `JaxRsClient#create` factory comes in two flavours: one for creating immutable clients given a fixed
`ClientConfiguration`, and one for creating mutable clients whose configuration (e.g., server URLs, timeouts, SSL
configuration, etc.) changes when the underlying `ClientConfiguration` changes.

## conjure-java-jersey-server
Provides Dropwizard/Jersey configuration for handling conjure types, and also exception mappers for translating common
runtime exceptions as well as our own `ServiceException` (see the [errors section](#errors-conjure-java-runtime-api))
to appropriate HTTP error codes. A Dropwizard server is configured for conjure as follows:

```java
public class MyServer extends Application<Configuration> {
    @Override
    public final void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(ConjureJerseyFeature.INSTANCE);
        env.jersey().register(new MyResource());
    }
}
```

## tracing
Provides [Zipkin](https://github.com/openzipkin/zipkin)-style call tracing libraries. All `JaxRsClient` instances are instrumented by default. Jersey server instrumentation is enabled via the
`ConjureJerseyFeature` (see above).

Please refer to [tracing-java](https://github.com/palantir/tracing-java) for more details on the `tracing` library usage.

## service-config (conjure-java-runtime-api)
Provides utilities for setting up service clients from file-based configuration. Example:

```yaml
# config.yml
services:
  security:
    # default truststore for all clients
    trustStorePath: path/to/trustStore.jks
  myService:  # the key used in `factory.get("myService")` below
    uris:
      - https://my-server/
    # optionally set a myService-specific truststore
    # security:
    #   trustStorePath: path/to/trustStore.jks
```

```java
ServiceConfigBlock config = readFromYaml("config.yml");
ServiceConfigurationFactory factory = ServiceConfigurationFactory.of(config);
HostMetricsRegistry hostMetricsRegistry = new HostMetricsRegistry();
MyService client = JaxRsClient.create(MyService.class, UserAgents.parse("my-agent"), hostMetricsRegistry, ClientConfigurations.of(factory.get("myService")));
```


## keystores and ssl-config (conjure-java-runtime-api)

Provides utilities for interacting with Java trust stores and key stores and acquiring `SSLSocketFactory` instances
using those stores, as well as a configuration class for use in server configuration files.

The `SslConfiguration` class specifies the configuration that should be used for a particular `SSLContext`. The
configuration is required to include information for creating a trust store and can optionally be provided with
information for creating a key store (for client authentication).

The configuration consists of the following properties:

* `trustStorePath`: path to a file that contains the trust store information. The format of the
  file is specified by the `trustStoreType` property.
* `trustStoreType`: the type of the trust store. See section below for details. The default value
  is `JKS`.
* (optional) `keyStorePath`: path to a file that contains the key store information. If unspecified,
  no key store will be associated with this configuration.
* (optional) `keyStorePassword`: password for the key store. Will be used to read the keystore
  provided by `keyStorePath` (if relevant for the format), and will also be used as the password
  for the in-memory key store created by this configuration. Required if `keyStorePath` is specified.
* (optional) `keyStoreType`: the type of the key store. See section below for details. The default
  value is `JKS`.
* (optional) `keyStoreAlias`: specifies the alias of the key that should be read from the key store
  (relevant for file formats that contain multiple keys). If unspecified, the first key returned by
  the store is used.

An `SslConfiguration` object can be constructed using the static `of()` factory methods of the
class, or by using the `SslConfiguration.Builder` builder. `SslConfiguration` objects can be
serialized and deserialized as JSON.

Once an `SslConfiguration` object is obtained, it can be passed as an argument to the
`SslSocketFactories.createSslSocketFactory` method to create an `SSLSocketFactory` object that can
be used to configure Java SSL connections.

#### Store Types

The following values are supported as store types:

* `JKS`: a trust store or key store in JKS format. When used as a trust store, the
  `TrustedCertificateEntry` entries are used as certificates. When used as a key store, the
  `PrivateKeyEntry` specified by the `keyStoreAlias` parameter (or the first such entry returned if
  the parameter is not specifeid) is used as the private key.
* `PEM`: for trust stores, an X.509 certificate file in PEM format, or a directory of such
  files. For key stores, a PEM file that contains a PKCS#1 RSA private key or PKCS#8 followed by the
  certificates that form the trust chain for the key in PEM format, or a directory of such files. In
  either case, if a directory is specified, every non-hidden file in the directory must be a file of
  the specified format (they will all be read).
* `PKCS12`: a trust store or key store in PKCS12 format. Behavior is the same as for the `JKS` type,
  but operates on stores in PKCS12 format.
* `Puppet`: a directory whose content conforms to the
  [Puppet SSLdir](https://docs.puppet.com/puppet/latest/reference/dirs_ssldir.html) format. For trust stores, the
  certificates in the `certs` directory are added to the trust store.  For key stores, the PEM files in the
  `private_keys` directory are added as the private keys and the corresponding files in `certs` are used as the trust
  chain for the key.

## errors (conjure-java-runtime-api)
Provides utilities for relaying service errors across service boundaries (see below).


# API Contract

conjure-java-runtime makes the following opinionated customizations to the standard Dropwizard/Feign behavior.

#### Object serialization/deserialization

All parameters and return values of `application/json` endpoints are serialized/deserialized to/from JSON using a
Jackson `ObjectMapper` with `GuavaModule`, `ShimJdk7Module` (same as Jackson’s `Jdk7Module`, but avoids Jackson 2.6
requirement) and `Jdk8Module`. Servers must not expose parameters or return values that cannot be handled by this object
mapper.


#### Error propagation
Servers should use the `ServiceException` class to propagate application-specific errors to its callers. The
`ServiceException` class exposes standard error codes that clients can handle in a well-defined manner; further,
ServiceException implements [SafeLoggable](https://github.com/palantir/safe-logging) and thus allows logging
infrastructure to handle "unsafe" and "safe" exception parameters appropriately. Typically, services define its error
types as follows:

```
class Errors {
  private static final ErrorType DATASET_NOT_FOUND =
    ErrorType.create(ErrorType.Code.INVALID_ARGUMENT, "MyApplication:DatasetNotFound");

  static ServiceException datasetNotFound(DatasetId datasetId, String userName) {
    // Both the safe and unsafe params will be sent back to the client; the client needs
    // to decide themselves if any of these are safe to log. We're only marking things as
    // safe or unsafe for this server to log.
    return new ServiceException(
            DATASET_NOT_FOUND, SafeArg.of("datasetId", datasetId), UnsafeArg.of("userName", userName));
  }
}

void someMethod(String datasetId, String userName) {
  if (!exists(datasetId)) {
    throw Errors.datasetNotFound(datasetId, userName);
  }
}
```

The `ConjureJerseyFeature` installs an exception mapper for `ServiceException`. The exception mapper sets the
response media type to `application/json` and returns as response body a JSON representation of a `SerializableError`
capturing the error code, error name, and error parameters. The resulting JSON response is:

```json
{
  "errorCode": "INVALID_ARGUMENT",
  "errorName": "MyApplication:DatasetNotFound",
  "errorInstanceId": "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx",
  "parameters": {
    "datasetId": "123abc",
    "userName": "yourUserName"
  }
}
```

JaxRsClient intercepts non-successful HTTP responses and throw a `RemoteException` wrapping the
deserialized server-side `SerializableError`. The error codes and names of the `ServiceException` and
`SerializableError` are defined by the service API, and clients should handle errors based on the error code and name:

```
try {
    service.someMethod();
catch (RemoteException e) {
    if (e.getError().errorName().equals("MyApplication:DatasetNotFound")) {
        handleError(e.getError().parameters().get("datasetId"));
    } else {
        throw new RuntimeException("Failed to call someMethod()", e);
    }
}
```

Frontends receiving such errors should use a combination of error code, error name, and parameters to display localized,
user friendly error information. For example, the above error could be surfaced as *"The requested dataset with id
123abc could not be found"*.

To support **legacy server implementations**, the `ConjureJerseyFeature` also installs exception mappers for
`IllegalArgumentException`, `NoContentException`, `RuntimeException` and `WebApplicationException`. The exceptions
typically yield `SerializableError`s with `exceptionClass=errorCode=<exception classname>` and
`message=errorName=<exception message>`. Clients should refrain from displaying the `message` or `errorName` fields to
user directly. Services should prefer to throw `ServiceException`s instead of the above, since they are easier to
consume for clients and support transmitting exception parameters in a safe way.

##### `RemoteException` vs `ServiceException` vs `SerializableError` vs `ErrorType`

 - `ErrorType` is a record type, meant to be used as 'compile time constants' - essentially used by services to define the 'enum' of their service exceptions
 - `SerializableError` defines the wire format for serializing ServiceExceptions in HTTP response bodies and contains the error code, error instance id, and application-defined parameters
 - `ServiceException` is a final subclass of `Exception`, thrown by the server
 - `RemoteException` is what the client sees if a remote call results in the server internally throwing a `ServiceException`

The workflow is:

 - Server code throws an instance of `ServiceException`, containing some `ErrorType`
 - The `com.palantir.conjure.java.server.jersey.ServiceExceptionMapper` exception mapper
   - determines the response code for this service exception
   - converts this into a `SerializableError`
   - serializes this into the response body as JSON
 - The client sees a `RemoteException`, which contains the `SerializableError` which was sent over the wire
 - The client can inspect the `SerializableError` and choose to act
 - If the client is itself a server and does not handle the `RemoteException`, a `SerializableError` error will be sent
   as the response with and `errorCode` of `INTERNAL`, `errorName` of `Default:Internal`, the same `errorInstanceId` as
   the original `RemoteException` and no `parameters`.

#### Serialization of Optional and Nullable objects
`@Nullable` or `Optional<?>` fields in complex types are serialized using the standard Jackson mechanism:
- a present value is serialized as itself (in particular, without being wrapped in a JSON object representing the `Optional` object)
- an absent value is serialized as a JSON `null`.
For example, assume a Java type of the form
```java
public final class ComplexType {
    private final Optional<ComplexType> nested;
    private final Optional<String> string;
}
```
, and an instance
```java
ComplexType value = new ComplexType(
        Optional.of(
                new ComplexType(
                        Optional.<ComplexType>absent(),
                        Optional.<String>absent(),
        Optional.of("baz"));
```
The JSON-serialized representation of this object is:
```json
{"nested":{"nested":null,"string":null},"string":"baz"}
```

#### Optional return values
When a call to a service interface declaring an `Optional<T>` return value with media type `application/json` yields:
- a `Optional#empty` return value, then the HTTP response has error code 204 and an empty response body.
- a non-empty return value, then the HTTP response has error code 200 and the body carries the deserialized `T` object
  directly, rather than a deserialized `Optional<T>` object.

JaxRsClients intercept such responses, deserialize the `T`-typed return value and return it to the caller wrapped as an
`Optional<T>`.

#### Call tracing

Clients and servers propagate call trace ids across JVM boundaries according to the
[Zipkin](https://github.com/openzipkin/zipkin) specification. In particular, clients insert `X-B3-TraceId: <Trace ID>`
HTTP headers into all requests which get propagated by Jetty servers into subsequent client invocations.

#### Endpoints returning plain strings

Endpoints returning plain strings should produce media type `text/plain`. Return type `Optional<String>` is only
supported for media type `application/json`.

#### Quality of service: retry, failover, throttling, backpressure

Flow control in Conjure is a collaborative effort between servers and clients.

Servers advertise an overloaded state using 429/503 responses, which clients interpret by throttling the number of in-flight requests they will send (currently according to an additive increase, multiplicative decrease based algorithm). Requests are retried a fixed number of times, scheduled with an exponential backoff algorithm.

Concurrency permits are only released when the response body is closed, so large streaming responses are correctly tracked.

conjure-java-runtime servers can use the `QosException` class to advertise the following conditions:

* `throttle`: Returns a `Throttle` exception indicating that the calling
  client should throttle its requests.  The client may retry against an arbitrary node of this service.
* `retryOther`: Returns a `RetryOther` exception indicating that the calling client should retry against the
  given node of this service.
* `unavailable`: An exception indicating that (this node of) this service is currently unavailable and the client
  may try again at a later time, possibly against a different node of this service.

The `QosExceptions` have a stable mapping to HTTP status codes and response headers:
* `throttle`: 429 Too Many Requests, plus optional `Retry-After` header
* `retryOther`: 308 Permanent Redirect, plus `Location` header indicating the target host
* `unavailable`: 503 Unavailable

conjure-java-runtime clients handle the above error codes and take the appropriate action:
* `throttle`: reschedule the request with a delay: either the indicated `Retry-After` period, or a configured
  exponential backoff
* `retryOther`: retry the request against the indicated service node; all request parameters and headers are maintained
* `unavailable`: retry the request on a different host after a configurable exponential delay

Additionally, connection errors (e.g., `connection refused` or DNS errors) yield a retry against a different node of the
service. Retries pick a target host by cycling through the list of URLs configured for a Service (see
`ClientConfiguration#uris`). Note that the "current" URL is maintained across calls; for example, if a first call yields
a `retryOther`/308 redirect, then any subsequent calls will be made against that URL. Similarly, if the first URL yields
a DNS error and the retried call succeeds against the URL from the list, then subsequent calls are made against that URL.

The number of retries for `503` and connection errors can be configured via `ClientConfiguration#maxNumRetries` or
`ServiceConfiguration#maxNumRetries`, defaulting to 4.

#### Metrics

The `HostMetricsRegistry` uses `HostMetrics` to track per-host response metrics. `HostMetrics` provides the following metrics:
- `get1xx()`: A timer of 1xx responses.
- `get2xx()`: A timer of 2xx responses.
- `get3xx()`: A timer of 3xx responses.
- `get4xx()`: A timer of 4xx responses, excluding 429s.
- `get5xx()`: A timer of 5xx responses, excluding 503s.
- `getQos()`: A timer of 429 and 503 responses.
- `getOther()`: A timer of all other responses.
- `getIoExceptions()`: A timer of all failed requests.

# License
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
