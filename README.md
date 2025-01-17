## Running the app locally for development
### Setup
1. Run `docker-compose up`. This will create a Localstack and initialize it with everything under `./src/main/resources/localstack/`.
2. Wait for localstack to start up and initialize. If it's not ready, the app will crash on boot. 
3. Start the application on CLI or IntelliJ Maven configuration via `mvn clean compile exec:java -Dvertx-config-path=conf/local-config.json -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory -Dlogback.configurationFile=conf/logback.xml`
4. Once started, admin service runs on `http://localhost:8089/`

### Test data
The data in LocalStack is ephemeral so any changes you make to it will disappear on container restart. If you want
to make permanent changes to test data setup, please change corresponding files under `./src/main/resources/localstack`
and re-initialize your localstack by running `docker-compose restart`.

### Authentication and Authorization
When running locally, GitHub OAuth 2.0 is disabled and users are logged in as *test.user@uidapi.com* via the 
`is_auth_disabled` flag. The user has all the rights available. To change the user rights, make changes to 
`src/main/resources/localstack/s3/admins/admins.json` and `docker-compose restart`.

If you want to test with GitHub OAuth 2.0, you will need to create an OAuth application on GitHub with `http://localhost:8089/oauth2-callback` as the callback URL, then generate a client ID/secret. Once generated, set the `is_auth_disabled` flag to `false`, and copy the client ID/secret into `github_client_id` and `github_client_secret`.

### Working on the UI
Per the above setup steps, the UI runs on `http://localhost:8089/`. To see your local UI changes reflected in the browser, you will need to hard reload (`Crtl+Shift+R`) while on the specific web page you have changed. 

## V2 API

The v2 API is based on individual route provider classes. Each class should provide exactly one endpoint and must implement IRouteProvider or IBlockingRouteProvider. 

### IRouteProvider

**Caution:** When implementing an API endpoint, you need to decide whether you should have a blocking or a non-blocking handler. Non-blocking handlers are suitable for most read-only operations, while most write operations should be done on a blocking handler. If you are calling into a service with a `synchronized` block, you **must** use a blocking handler. You can make your handler blocking by implementing the `IBlockingRouteProvider` interface *instead of* the `IRouteProvider` interface.

IRouteProvider requires a `getHandler` method, which should return a valid handler function - see `GetClientSideKeypairsBySite.java`. This method *must* be annotated with the Path, Method, and Roles annotations.

The route handler will automatically be wrapped by the Auth middleware based on the roles specified in the Roles annotation.
