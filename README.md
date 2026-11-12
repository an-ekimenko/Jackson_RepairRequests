# RepairRequests

**Extracts, parses, filters, and stores gas station equipment repair requests from the 1C:TOiR REST API into a Microsoft SQL Server staging environment for SAP BW/4HANA.**

---

## Overview

**RepairRequests** is a modular Java 17+ command-line ETL utility built on **Apache Maven** and **Google Guice 7**. It fetches repair request records from the 1C:TOiR HTTP gateway over HTTPS for the current calendar year (`Europe/Minsk` timezone), cleanses and deserializes the JSON payload using **Jackson**, filters records for critical gas station equipment (coffee machines, tire inflation stations, vacuum cleaners), performs in-memory composite-key deduplication, and executes transactional batch inserts/updates into a **Microsoft SQL Server** staging database via **Hibernate 6+**. Upon completion, the application generates a machine-readable execution summary in `exchange/result.yaml` and returns standard OS exit codes (`0` or `1`) for integration with enterprise job schedulers.

---

## Key Architectural Highlights

* **Dependency Injection Architecture:** Powered by **Google Guice 7** (`RRAppModule`), decoupling configuration, networking, serialization, and persistence layers.
* **1C:TOiR & IIS Resilient Network Infrastructure (`RR_HttpClient`):**
  * **State-Aware Lazy Initialization:** Defers `CloseableHttpClient` instantiation until after `CertUpdater` synchronizes the remote TLS certificate chain into the local JVM `cacerts` store.
  * **IIS / Stateless 1C Compatibility:** Splits target endpoints into `HttpHost` + `relativePath`, enforces *Preemptive Basic Authentication* (`BasicAuthCache` + `HttpClientContext`), and retains explicit `Host` and `Authorization` headers to comply with `wsisapi.dll` gateway constraints.
  * **Multi-Schema Authentication:** Supports `none`, `basic`, static `token`, and `oauth2` (Client Credentials Grant with in-memory token caching and expiration tracking).
  * **Connection Pooling & Retries:** Backed by `PoolingHttpClientConnectionManager` with configurable connection/response timeouts and `DefaultHttpRequestRetryStrategy` (up to 5 attempts at 30-second intervals).
* **Fault-Tolerant Data Parsing (`RR_DataDeserializer`):** Safely extracts 1C date-time strings (`dd.MM.yyyy H:mm:ss`), sanitizes dirty numeric strings while preserving leading minus signs, and prevents `Integer` overflow exceptions.
* **Domain Filtering & Composite-Key Deduplication (`RR_App`):** Filters incoming payloads by critical equipment SUID suffixes (`_CF`, `_PPS`, `_VC`) and deduplicates records by composite primary key (`RepairRequests_PK`) via Java Streams prior to database persistence.
* **High-Throughput Batch Persistence (`RepairRequests_DAO`):** Uses Hibernate 6+ `merge()`, periodic `flush()`, and `clear()` every 50 records to prevent first-level cache memory overhead.
* **Environment-Aware Configuration (`ConfigPathResolver`):** Dynamically locates external `config/properties.yaml` and `config/Hibernate.cfg.xml` files whether executed from an IDE (`target/classes`) or a packaged standalone JAR.
* **Execution Reporting & Auditing:** Writes structured status summaries (`datetime`, `returned_code`, `records_processed`) to `exchange/result.yaml` and captures application and raw SQL logs via **Log4j2** and **P6Spy**.

---

## Project Structure

```text
RepairRequests/
├── config/
│   ├── Hibernate.cfg.xml.dist         # Hibernate & MS SQL database connection template
│   └── properties.yaml.dist           # TOiR API endpoint, timeouts, and auth template
├── exchange/
│   └── result.yaml                    # Auto-generated execution summary for job schedulers
├── logs/
│   ├── commons.log                    # Application runtime logs (Log4j2)
│   └── p6spy.log                      # SQL query execution trace logs (P6Spy)
├── src/
│   ├── main/
│   │   ├── java/repair_requests/
│   │   │   ├── config/
│   │   │   │   ├── ConfigPathResolver.java
│   │   │   │   ├── ToirApiConfig.java
│   │   │   │   └── YAMLConfigLoader.java
│   │   │   ├── hibernate/
│   │   │   │   └── RepairRequests_DAO.java
│   │   │   ├── jackson_json/
│   │   │   │   └── RR_DataDeserializer.java
│   │   │   ├── model/
│   │   │   │   ├── ExecutionResult.java
│   │   │   │   ├── RepairRequests_Data.java
│   │   │   │   ├── RepairRequests_Entity.java
│   │   │   │   └── RepairRequests_PK.java
│   │   │   ├── network/
│   │   │   │   └── RR_HttpClient.java
│   │   │   ├── util/
│   │   │   │   ├── LogInitializer.java
│   │   │   │   └── ResultFileWriter.java
│   │   │   ├── MainLauncher.java
│   │   │   ├── RRAppModule.java
│   │   │   └── RR_App.java
│   │   └── resources/
│   │       ├── Log4j2.xml
│   │       └── spy.properties
│   └── test/
│       └── java/repair_requests/
│           ├── jackson_json/
│           │   └── RR_DataDeserializerTest.java
│           ├── network/
│           │   └── RR_HttpClientTest.java
│           └── RR_AppTest.java
├── pom.xml
└── README.md
```

---

## Key Components Breakdown

### Entry Point & Orchestration (`repair_requests`)
* **`MainLauncher`** — The definitive application entry point. Initializes runtime logging and JVM `trustStore` properties via `LogInitializer`, bootstraps the Guice DI container, invokes `RR_App`, safely closes the Hibernate `SessionFactory`, writes the execution summary to `exchange/result.yaml`, and exits with code `0` (success) or `1` (failure).
* **`RRAppModule`** — The Google Guice 7 configuration module binding infrastructure singletons: `ToirApiConfig`, a configured Jackson `ObjectMapper` with `RR_DataDeserializer`, `HttpClientBuilder`, and the Hibernate `SessionFactory`.
* **`RR_App`** — The stateless ETL pipeline orchestrator. Fetches raw JSON via `RR_HttpClient`, deserializes payloads, filters records matching target equipment SUID suffixes (`_CF`, `_PPS`, `_VC`), deduplicates entities by composite business key (`RepairRequests_PK`), and persists batches via `RepairRequests_DAO`.

### Configuration (`repair_requests.config`)
* **`ConfigPathResolver`** — Detects the execution context (IDE `target/classes` vs. standalone JAR) and resolves absolute paths to `config/`, `logs/`, and `exchange/` directories relative to the application root.
* **`ToirApiConfig`** — Jackson DTO mapping the `toir-api` root section of `properties.yaml`, including `data_endpoint`, `auth_type`, nested authentication blocks (`basic`, `static_token`, `oauth2`), and HTTP timeout parameters (`connection`).
* **`YAMLConfigLoader`** — Loads and validates `config/properties.yaml` using Jackson's `YAMLFactory` with strict *Fail-Fast* error handling.

### Network Layer (`repair_requests.network`)
* **`RR_HttpClient`** — Guice-managed singleton wrapping **Apache HttpClient 5**. Dynamically calculates `DateBegin` and `DateEnd` parameters for the current year (`Europe/Minsk`), synchronizes TLS certificates via `CertUpdater` before `SSLContext` initialization, enforces Preemptive Basic Auth and IIS-compatible headers, caches OAuth2 tokens, and outputs wire diagnostics.

### Serialization, Domain Models & Persistence (`jackson_json`, `model`, `hibernate`)
* **`RR_DataDeserializer`** — Custom Jackson `StdDeserializer` that safely parses Russian-keyed JSON fields from 1C:TOiR, converting `dd.MM.yyyy H:mm:ss` timestamps and stripping non-numeric artifacts from integer fields without throwing overflow exceptions.
* **`RepairRequests_Data`** — DTO representing a raw repair request object from the upstream 1C:TOiR API.
* **`RepairRequests_Entity` & `RepairRequests_PK`** — JPA entity and `@Embeddable` composite primary key (`Date`, `ApplicationNumber`, `OrganizationCodeMDM`, `DepartmentCodeMDM`) mapped to the target `RepairRequests` table in MS SQL Server.
* **`ExecutionResult`** — DTO modeling the YAML summary payload written to `exchange/result.yaml`.
* **`RepairRequests_DAO`** — Guice singleton managing transactional batch persistence into MS SQL Server with periodic `flush()` and `clear()` calls every 50 records.

### Utilities (`repair_requests.util`)
* **`LogInitializer`** — Sets `javax.net.ssl.trustStore` (`lib/security/cacerts`) and `log.dir` system properties at startup and forces `Log4j2` context reconfiguration.
* **`ResultFileWriter`** — Serializes `ExecutionResult` metrics (`datetime`, `returned_code`, `records_processed`) into `exchange/result.yaml`.

---

## Prerequisites

* **Java Development Kit (JDK):** Version **17** or higher.
* **Apache Maven:** Version **3.8+** (for dependency resolution, testing, and JAR packaging).
* **Database:** Microsoft SQL Server instance accessible via JDBC with the target `RepairRequests` table created.
* **Internal Cryptographic Module:** `by.beloil.crypto:crypto-cert-loader:0.0.1-SNAPSHOT` installed in your local Maven repository (`~/.m2`) or corporate Nexus/Artifactory.

---

## Configuration Setup

Before running the application, copy the `.dist` template files inside the root `config/` directory and remove the `.dist` suffix:

1. **API & Authentication Settings (`config/properties.yaml`):**
   Copy `config/properties.yaml.dist` to `config/properties.yaml` and configure your 1C:TOiR endpoint, timeouts, and authentication strategy (`none`, `basic`, `token`, or `oauth2`):
   ```yaml
   toir-api:
     data_endpoint: "https://your-1c-host/toir/hs/RepairRequests/get"
     auth_type: "basic"
     connection:
       connect_timeout_ms: 50000
       response_timeout_ms: 120000
     auth:
       basic:
         username: "YOUR_USERNAME"
         password: "YOUR_PASSWORD"
   ```

2. **Database Connection (`config/Hibernate.cfg.xml`):**
   Copy `config/Hibernate.cfg.xml.dist` to `config/Hibernate.cfg.xml` and update the JDBC connection URL (wrapped with P6Spy), credentials, and SQL Server dialect:
   ```xml
   <property name="hibernate.connection.driver_class">com.p6spy.engine.spy.P6SpyDriver</property>
   <property name="hibernate.connection.url">jdbc:p6spy:sqlserver://YOUR_DB_HOST:1433;databaseName=YOUR_DB;encrypt=true;trustServerCertificate=true;</property>
   <property name="hibernate.connection.username">YOUR_DB_USER</property>
   <property name="hibernate.connection.password">YOUR_DB_PASSWORD</property>
   ```

> **Security Note:** Never commit `config/properties.yaml` or `config/Hibernate.cfg.xml` containing production credentials to version control. Ensure both files are listed in `.gitignore`.

---

## Output & Monitoring

* **Execution Summary (`exchange/result.yaml`):** Generated automatically upon completion for external job schedulers (e.g., Windows Task Scheduler, cron, Airflow):
  ```yaml
  datetime: "2026-11-12 14:00:00"
  returned_code: 0
  records_processed: 42
  ```
  * `returned_code: 0` — Pipeline completed without errors.
  * `returned_code: 1` — A fatal configuration, network, parsing, or database error occurred.
* **Application Logs (`logs/commons.log`):** Detailed execution trace, HTTP request/response diagnostics, and error stack traces managed by Log4j2.
* **SQL Audit Logs (`logs/p6spy.log`):** Intercepted JDBC statements, bound parameter values, and query execution timings captured by P6Spy.

---

## Testing Architecture

The test suite (`src/test/java/repair_requests/`) uses **JUnit 5** and **Mockito** (`MockedStatic`) to verify core business logic in complete isolation from external network and database resources:

* **`RR_DataDeserializerTest`** — Parameterized tests validating 1C timestamp parsing, safe handling of blank/null fields, sanitization of dirty numeric strings (preserving leading minus signs), and `Integer` overflow protection.
* **`RR_HttpClientTest`** — Verifies dynamic `DateBegin`/`DateEnd` URI generation, Preemptive Basic Auth (`BasicAuthCache`), static Bearer token headers, *Fail-Fast* config validation, and TLS certificate synchronization via `MockedStatic<CertUpdater>`.
* **`RR_AppTest`** — End-to-end integration tests verifying SUID equipment filtering (`_CF`, `_PPS`, `_VC`), composite primary key (`RepairRequests_PK`) deduplication, and empty payload handling.
!!!!!


### 2. URL UserInfo Deprecation Policy (`ClientProtocolException`)
* **Issue:** Attempting to pass credentials directly within the URL string (`https://domain.by...`) caused the application to crash locally with a `Request URI authority contains deprecated userinfo component` validation exception.
* **Root Cause:** Apache HttpClient 5 strictly enforces the modern **RFC 3986** security specification, inherently blocking raw plain-text credentials embedded inside URL strings to mitigate data leakage risks.
* **Solution:** Decoupled the physical server address from the request structure. The application now extracts and routes the raw execution path via `relativePath`, while wrapping the physical endpoint context inside a dedicated low-level `HttpHost` configuration component.

---

## 🚀 Features

* **Resilient Enterprise Networking:** Utilizes **Apache HttpClient 5** with a built-in connection pool (`PoolingHttpClientConnectionManager`), providing a solid foundation for robust, high-performance web service interactions.
* **Automated Fault-Tolerance:** Replaces fragile manual loops with a declarative `DefaultHttpRequestRetryStrategy` executing an exponential backoff algorithm (up to 5 attempts, starting at 30 seconds) to survive temporary network failures.
* **On-The-Fly SSL Certificate Management:** Features an advanced `KeyStoreManager` that programmatically intercepts remote certificate chains during SSL handshakes and updates the local JVM `cacerts` file. It hot-swaps the memory-resident `SSLContext` dynamically without requiring an application restart.
* **Robust JSON Deserialization:** Configured via Jackson's `ObjectMapper` to gracefully ignore undeclared or unknown API properties (`FAIL_ON_UNKNOWN_PROPERTIES`), preventing runtime crashes during upstream schema updates.
* **Defensive Parsing & Stream Transformation:** Employs a custom `RR_DataDeserializer` registered via a `SimpleModule` for type-safe mapping. Uses Java Streams to map raw DTO payload arrays into persistent Hibernate entities.
* **High-Performance Staging Persistence:** Executes optimized, transaction-aware batch operations (`saveOrUpdateBatch`) directly through the DAO layer to minimize round-trips to the MS SQL database.
* **Pluggable Multi-Schema Authentication:** Out-of-the-box support for `basic`, `token`, or `none` API authorization schemes, dynamically driven by external configuration files.

---

## 📋 Description

The **Jackson_RepairRequests** application extracts, parses, and stores pending (unclosed) equipment repair requests from gas stations. The application consumes **JSON payloads** from a 1C:TOiR web service and writes the processed records to an **MS SQL** target database, which serves as a reliable staging area for subsequent ETL loads into **SAP BW/4HANA**.

To optimize data relevance, the system selectively filters and processes data for a predefined set of critical gas station equipment codes, specifically targeting:
* **Coffee machines**
* **Vacuum cleaners**
* **Tire inflation stations**

---

## 🛠️ Technical Overview

* **Authentication & Security:** Authorizes API requests using flexible schema support (`basic`, `token`, or `none`), fully configurable via the `properties.yaml` file. Secure resource access is enforced through an internal custom library that dynamically manages and updates SSL certificate states based on the active authentication context.
* **Data Parsing & Binding:** Utilizes the **Jackson** library to deserialize JSON payloads from the web service into intermediate DTOs before converting them into Hibernate entities. Additionally, Jackson is used to parse the application's YAML configuration file into structured properties.
* **OR/M Layer:** Employs **Hibernate** to manage data persistence and lifecycle operations within the target MS SQL database.
* **Logging & Auditing:** Captures application metrics and raw database transactions using **P6Spy** and **Log4j2**. P6Spy intercepts native database requests and routes them via the unified **SLF4J** facade to Log4j2, enabling structured logging of formatted SQL queries populated with actual execution parameters.

---

## 🏗️ Shared Dependency Architecture

To eliminate library redundancy and optimize infrastructure disk usage across multiple similar integration applications, this project adopts a decoupled shared-dependency deployment model:

* **Centralized Dependencies:** All required external runtime JAR libraries are compiled and isolated into a dedicated `dependencies` directory.
* **Directory Layout:** The dependencies folder is located one level above the application root directory (`Jackson_RepairRequests`), serving as a common binary store for neighboring integration modules:

```text
├── shared-environment-root/
│   ├── dependencies/              <-- Shared runtime external libraries (.jar)
│   └── Jackson_RepairRequests/    <-- This application root directory
│       ├── src/
│       ├── target/
│       └── pom.xml
```

* **Classpath Decoupling:** The compiled application artifact excludes third-party binaries inside its own structure, dynamically linking against the shared directory at runtime via classpath definitions.

---

## 📂 Project Structure & Configuration

The project follows a clean, layered architecture designed for deterministic batch processing:

### ⚙️ Configuration & Security Management
> ⚠️ **Security Note:** Real production configuration files containing plain-text credentials (`properties.yaml` and `hibernate.cfg.xml`) are strictly tracked by `.gitignore` and excluded from the Git repository to prevent accidental credential leakage. Instead, public safe templates are provided.

* `properties.example.yaml` — A safe template for API authorization modes, targets, and environment settings. Must be copied and renamed to `properties.yaml` locally.
* `hibernate.cfg2.xml.example` — A safe template for MS SQL connection strings and Hibernate parameters. Must be copied and renamed to `hibernate.cfg.xml` locally.
* `repair_requests.config.ToirApiConfig` — A DTO class for deserializing TOiR API connection parameters from the YAML configuration file using Jackson. Includes a nested structure for flexible authentication handling.
* `LogConfigUtil` — A utility class responsible for dynamic runtime initialization and configuration of the Log4j2/SLF4J logging subsystem.

### 🗃️ Domain Model & Data Transfer (DTO)
* `repair_requests.model.RepairRequests_Entity` — A Hibernate entity class representing the target `RepairRequests` database table.
* `repair_requests.model.RepairRequests_PK` — An embedded primary key class enforcing data integrity constraints for `RepairRequests_Entity`.
* `repair_requests.model.RepairRequests_Data` — A DTO class representing a repair request object mapped directly from the raw JSON structure.

### 🔄 JSON Processing & Orchestration
* `repair_requests.jackson_json.RR_DataDeserializer` — A custom Jackson deserializer designed for defensive extraction of API fields, safely mapping missing or empty JSON nodes into appropriate Java objects and handling numerical overflows.
* `RepairRequests_App` — The main entry point of the application containing the core execution orchestration flow and the `main()` method.

### 💾 Persistence Layer
* `repair_requests.hibernate.RepairRequests_DAO` — A helper DAO class that manages transactional batch database operations (`merge`, `flush`, `clear`) to safely persist data without memory leaks.
* `HibernateUtil` — A utility class for managing the thread-safe Hibernate `SessionFactory` singleton instance using safe lazy initialization.

---

## 📋 Prerequisites

* **Java:** **JDK 17 or higher** *(required by Hibernate 6+, Google Guice 7, Apache HttpClient 5, and Java 14+ Switch Expressions)*
* **Database:** MS SQL Server
* **Build System:** Maven (configured via `pom.xml`)

---

## 💻 Deployment & Running

1. **Prepare Environment Configurations:**
   Since production credentials are safe from tracking via `.gitignore`, initialize your active operational profiles from the provided templates:
   * Copy `properties.example.yaml` to a new file named `properties.yaml`, then update the target TOiR API endpoints and active authorization credentials.
   * Copy `hibernate.cfg2.xml.example` to a new file named `hibernate.cfg.xml`, then populate it with your actual MS SQL database endpoint parameters.

2. **Build the Application:** Execute the Maven package phase to compile code and build the executable asset:
   ```bash
   mvn clean package
   ```

3. **Execute the Application:**

   **A. Standard Execution Mode**

   Run the compiled JAR file natively using the Java CLI. Thanks to the automated manifest configuration in `pom.xml`, the classpath linking to the shared `../dependencies/` folder is resolved out of the box, and the internal logging engine dynamically manages its output file storage targets relative to your current execution scope:

   ```bash
   java -jar /path/to/jck_RepairRequests-0.0.1-SNAPSHOT.jar
   ```

   **B. Dynamic Log Directory Overriding**

   You can dynamically override the absolute destination folder for all runtime traces (`commons.log` and `p6spy.log`) at startup by supplying the `-Dlog.dir` system property flag to the JVM configuration:

   ```bash
   java -Dlog.dir=/var/log/RepairRequests -jar /path/to/jck_RepairRequests-0.0.1-SNAPSHOT.jar
   ```

