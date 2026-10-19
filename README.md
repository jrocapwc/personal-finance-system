# Personal Finance Management System

This milestone configures only `core-api`: Java 25, Spring Boot 4.1.1,
Maven, and H2. It provides application startup, database connectivity, and
Actuator health. No financial tables or business features are created.

## Setup (Windows PowerShell)

Install a JDK 25 and set `JAVA_HOME` to its installation directory. In a new
PowerShell window, run these commands from the repository root:

```powershell
# Adjust this example to your JDK 25 installation.
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
Push-Location core-api
.\mvnw.cmd --version
.\mvnw.cmd clean compile
```

Both version commands should report Java 25. Maven Wrapper downloads Maven on
first use; internet access is needed for Maven and uncached dependencies.
If Maven is already installed, `mvn` can replace `.\mvnw.cmd` in these commands.
No external database, containers, or `.env` file is needed.

## Tests

From `core-api`:

```powershell
.\mvnw.cmd test
# Full build, tests, and packaged executable JAR:
.\mvnw.cmd verify
```

The startup test explicitly activates `test`, starts an HTTP server on a random
port, checks that `/actuator/health` returns HTTP 200 with JSON status `UP`,
and checks that the H2 console returns HTTP 404. The test database exists only
in memory and disappears when the test JVM exits. Tests do not use the local
file database.

## Start locally

From `core-api`:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

The profile is selected explicitly. Run from `core-api` so the relative JDBC
URL `jdbc:h2:file:./data/personal-finance` consistently uses
`core-api/data/personal-finance.mv.db`. H2 creates the database file on first
connection and preserves it across restarts. Stop the API with Ctrl+C.
Run `Pop-Location` when finished with the commands in this directory.

## Manual verification

While the API is running, open another PowerShell window:

```powershell
$response = Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health
$response.StatusCode
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Compress
Start-Process 'http://localhost:8080/h2-console'
```

Expect HTTP `200` and JSON containing `"status":"UP"` (Boot may also list health groups). The health check includes database
connectivity. Only the health Actuator endpoint is exposed, without component
details. `GET /` returns 404 because no application controller exists yet.

In the H2 console, enter:

| Setting | Value |
| --- | --- |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/personal-finance` |
| User Name | `sa` |
| Password | Leave blank |

Connect while the API is running and execute:

```sql
SELECT 1;
SHOW TABLES;
```

Expect `1` and no application tables. The console is enabled only by the `local`
profile and accepts only local connections. The blank password is for this local
development setup. Stop and restart with the same command to verify the same
file database opens again. H2 database files are ignored by Git:

```powershell
# From the repository root:
git check-ignore core-api/data/personal-finance.mv.db
```

## Important files

| File | Purpose |
| --- | --- |
| `core-api/pom.xml` | Selects Java 25 and Spring Boot 4.1.1; includes Web MVC, Data JPA, H2 and its console module (runtime), Validation, Actuator, and Spring Boot Test (test scope). Boot manages dependency versions. |
| `core-api/src/main/java/core_api/CoreApiApplication.java` | Existing application entry point; starts Spring Boot and component scanning. |
| `core-api/src/main/resources/application.properties` | Shared settings: `spring.jpa.hibernate.ddl-auto=none`, SQL initialization disabled, console disabled by default, and health-only Actuator exposure. |
| `core-api/src/main/resources/application-local.properties` | File-based H2 connection and local console settings; overrides shared settings when `local` is active. |
| `core-api/src/test/resources/application-test.properties` | In-memory H2 connection for tests; packaged only with test resources, with the console disabled. |
| `core-api/src/test/java/core_api/CoreApiApplicationTests.java` | Context startup, real HTTP health, and disabled-console checks using the test profile. |
| `core-api/.gitignore` | Ignores build/IDE artifacts and H2 database, trace, lock, and temporary files. |
| `core-api/.mvn/wrapper/maven-wrapper.properties` | Pins the Maven distribution used by `mvnw.cmd` and `mvnw`. |

Hibernate does not create or update tables. SQL initialization is also disabled.
No Flyway dependency, configuration, or migration was present during inspection;
none is added in this milestone.

## Troubleshooting

- **Wrong Java version:** check `java -version`, `$env:JAVA_HOME`, and
  `.\mvnw.cmd --version`. Set `JAVA_HOME` to JDK 25 and put its `bin` first on
  `PATH` in the same PowerShell session.
- **Maven download failure:** check network/proxy access to Maven Central and
  write access to the Maven cache. Installed Maven is an alternative.
- **Port 8080 occupied:** set `$env:SERVER_PORT = '8081'` before starting the API
  and use port 8081 for health and console URLs.
- **H2 database locked:** stop any other API process using the same database file.
  Use the running API's browser console for access.
- **Console missing:** start with the `local` profile and visit `/h2-console`.
- **Health DOWN:** inspect startup logs and confirm the `data` directory is
  writable. Use the exact JDBC URL above in the console.
