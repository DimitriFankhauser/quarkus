# Notes Dimitri 

## Commands

Build Quarkus 

    mvn clean install -DskipTests -DskipITs -DskipDocs
    
## Callstack

`VertxHttpProcessor.openSocket -> VertxHttpRecorder.startServer/startServerAfterFailedStart -> VertxHttpRecorder.doServerStart -> VertxHttpRecorder.initializeMainHttpServer -> HttpServerOptionUtils.createSslOptions -> HTTPServerOptionUtils.applySslConfigToHttpServerOptions ->  TLSUtils.computeKeyStoreOptions`

`VertxHttpProcessor.openSocket -> VertxHttpRecorder.startServer/startServerAfterFailedStart -> VertxHttpRecorder.doServerStart -> VertxHttpRecorder.initializeManagementInterface -> HttpServerOptionUtils.createSslOptionsForManagementInterface -> HTTPServerOptionUtils.applySslConfigToHttpServerOptions -> TLSUtils.computeKeyStoreOptions`


## Other Findings
```
VertxHttpRecorder Lines 843-845

        // Disable TLS if certificate options are still missing after customize hooks.
        if (tmpSslConfig != null && tmpSslConfig.getKeyCertOptions() == null) {
            tmpSslConfig = null;
        }

```
---

This seems to be the attribute for mTLS
`

        VertxHttpBuildTimeConfig Lines 42-45

        @WithName("ssl.client-auth")
        @WithDefault("NONE")
        ClientAuth tlsClientAuth();
`

---
## Callstack for TLSConfig 

```VertxHttpProcessor.finalizeRouter``` has ``` TlsRegistryBuildItem tlsRegistryBuildItem``` in it's constructor. 
``` TlsRegistryBuildItem``` has ```Supplier<TlsConfigurationRegistry> reference```in it's constructor. 
```TlsConfigurationRegistry``` can register objects of type ```TlsConfiguration```
```TLSConfiguration```has a method getKeyStore() and is implemented by ```VertxCertificateHolder```for instance. Objects of `VertxCertificateHolder` Type are in turn created by CertificateRecorder.verifyCertificateConfigInternal. `CertificateRecorder.verifyCertificateConfigInternal` is called by `CertificateRecorder.validateCertificates` which in turn is called by `CertificateProcessor.initializeCertificate`


---

I think this is the relevant CallStack: 

`

    VertxHttpRecorder.startServer Line 377
    
    VertxHttpConfig httpConfiguration = this.httpConfig.getValue();
`

the type of `httpConfig` here is `RuntimeValue<VertxHttpConfig>`. The Interface `VertxHttpConfig` declares the method `ServerSslConfig ssl();` The interface `ServerSslConfig` in turn declares the method `CertificateConfig certificate();` The interface `CertificateConfig` declares the method` Optional<String> keyStoreAlias();`. 

## CDI and Beans 
This interface `CertificateConfig` has no implementations, yet it's used as a parameter in `TlsUtils.computeKeyStoreOptions(CertificateConfig certificates, Optional<String> keyStorePassword,Optional<String> keyStoreAliasPassword)` 


`



## How Configuration Works 
The Interface `VertxHttpOptions` is annotated with `@ConfigMapping(prefix = "quarkus.http")` and based on [this documentation](https://quarkus.io/guides/http-reference#listening-on-a-random-port), this means attributes in this class can be set using the application.properties file. I've read some more in some of the Smallrye classes, but my understanding is incomplete so far. 


## How HTTP-Options are Deployed to Vertx

VertxHttpRecorder.initializeMainHttpServer, Lines 874-892


        vertx.deployVerticle(new Supplier<>() {
            @Override
            public Verticle get() {
                return new WebDeploymentVerticle(
                        httpMainServerOptions, httpMainSslServerOptions, httpMainDomainSocketOptions,
                        launchMode, insecureRequestStrategy, connectionCount, registry, startEventsFired,
                        httpBuildTimeConfig, httpConfig, registerHttpServer, registerHttpsServer);
            }
        }, new DeploymentOptions().setInstances(ioThreads), new Handler<>() {
            @Override
            public void handle(AsyncResult<String> event) {
                if (event.failed()) {
                    futureResult.completeExceptionally(event.cause());
                } else {
                    futureResult.complete(event.result());
                }
            }
        });

        return futureResult;

as shown here `httpConfig` and `httpBuildTimeConfig` are given to the new `WebDeploymentVerticle`. 
For now `httpBuildTimeConfig` is irrelevant, but `httpConfig`is of the type `VertxHttpConfig` which contains the function
`ServerSslConfig ssl();`

The interface `ServerSslConfig` of course has the function `CertificateConfig certificate();` As mentioned before `CertificateConfig` includes functions like `Optional<String> keyStorePassword();`


Looking into the inline class `WebDeploymentVerticle`(Filename=VertxHttpRecorder) one can quickly see this: 
`WebDeploymentVerticle` extends `AbstractVerticle`and has  `HttpServerOptions httpsOptions` as a parameter. The "Goal" of this class is to run `vertx.createHttpServer(HttpServerOptions options)`. `HttpServerOptions` extends `NetServerOptions` (VertX) and has the following Methods, that seem relevant 


`  

    @Override
    public HttpServerOptions setKeyCertOptions(KeyCertOptions options) {
        super.setKeyCertOptions(options);
        return this;
    }

    @Deprecated
    @Override
    public HttpServerOptions setKeyStoreOptions(JksOptions options) {
        super.setKeyStoreOptions(options);
        return this;
    }

    @Deprecated
    @Override
    public HttpServerOptions setPfxKeyCertOptions(PfxOptions options) {
        return (HttpServerOptions) super.setPfxKeyCertOptions(options);
    }

    @Deprecated
    @Override
    public HttpServerOptions setPemKeyCertOptions(PemKeyCertOptions options) {
        return (HttpServerOptions) super.setPemKeyCertOptions(options);
    }
  `
---

# What TlsUtils does



