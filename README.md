# RepairRequests

**Extracts, parses, filters, and stores gas station equipment repair requests from the 1C:TOiR REST API into a Microsoft SQL Server staging environment for SAP BW/4HANA.**

---

## Overview

**RepairRequests** (`Jackson_RepairRequests`) is a modular Java 17+ command-line ETL utility built on **Apache Maven** and **Google Guice 7**. It automates fetching, filtering, deduplicating, and staging repair request records from 1C:TOiR into MS SQL Server, generating execution summaries in YAML and standard OS exit codes.

---

## 🚀 Features

* **Modular DI Architecture:** Built on Google Guice 7 (`RRAppModule`) with clear separation of configuration, networking, and persistence.
* **Resilient Enterprise Networking:** Uses Apache HttpClient 5 with connection pooling, Preemptive Basic Auth, and wire-level tracing.
* **Exponential Backoff with Jitter:** Configurable retry strategy (`properties.yaml`) to handle transient network issues without thundering-herd effects.
* **Dynamic SSL Certificate Management:** Features `CertUpdater` and `KeyStoreManager` to synchronize remote certificate chains with the local JVM truststore.
* **Pluggable Authentication:** Supports `basic`, static `token`, `oauth2` (Client Credentials Grant with token caching), and `none` schemes.
* **Metadata Filtering & Stream Deduplication:** Custom `RR_DataDeserializer` filters payloads by equipment classifier codes and deduplicates composite keys via Java Streams.
* **High-Performance Staging Persistence:** Transaction-aware batch operations (`saveOrUpdateBatch`) managed by a Hibernate 6+ DAO singleton.
* **Monitoring & Scheduler Integration:** Generates `exchange/result.yaml` summary reports alongside standard OS exit codes (`0` or `1`) and audit logs.
---

## 🔍 Integration Challenges & Solutions

* **Anonymous IIS & Stateless 1C Mode:** IIS 10.0 with anonymous authentication forwards traffic directly to `wsisapi.dll` without HTTP 401 challenges, causing Apache HttpClient 5 to drop sessions. Resolved via **Preemptive Authentication** (`BasicCredentialsProvider`, `AuthCache`) forcing credentials in the first packet.
* **URL UserInfo Deprecation:** Passing credentials in URL strings triggers RFC 3986 exceptions in Apache HttpClient 5. Resolved by decoupling server addresses from request paths using `HttpHost` and `relativePath`.

---

## 🏗 Shared Dependency Architecture

The project uses a decoupled shared-dependency model to eliminate redundancy:

* **Centralized Dependencies:** Third-party JARs reside in a shared `../dependencies/` folder configured via Maven `Class-Path` manifest entries:

```text
shared-environment-root/
├── dependencies/          # Shared runtime external libraries (.jar)
└── Jackson_RepairRequests/ # Application root directory
    ├── config/ | exchange/ | logs/ | src/ | target/ | pom.xml
```
---

## 📂 Project Structure

```text
Jackson_RepairRequests/
├── config/
│   ├── Hibernate.cfg.xml.dist         # Hibernate & MS SQL database connection template
│   └── properties.yaml.dist           # TOiR API endpoint, equipment codes, timeouts, retry & auth template
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

## 🧩 Key Components Breakdown

### Entry Point & Orchestration (`repair_requests`)
* **`MainLauncher`** — The definitive application entry point. Initializes runtime logging and JVM `trustStore` properties via `LogInitializer`, bootstraps the Guice DI container, invokes `RR_App`, safely closes the Hibernate `SessionFactory`, writes the execution summary to `exchange/result.yaml`, and exits with code `0` (success) or `1` (failure).
* **`RRAppModule`** — The Google Guice 7 configuration module binding infrastructure singletons: `ToirApiConfig`, a configured Jackson `ObjectMapper` (injecting configured `equipment_classifier_codes` into `RR_DataDeserializer`), `HttpClientBuilder`, and the Hibernate `SessionFactory`.
* **`RR_App`** — The stateless ETL pipeline orchestrator. Fetches raw JSON via `RR_HttpClient`, deserializes filtered records, deduplicates entities by composite business key (`RepairRequests_PK`), and persists batches via `RepairRequests_DAO`.

### Configuration (`repair_requests.config`)
* **`ConfigPathResolver`** — Detects the execution context (IDE `target/classes` vs. standalone JAR) and resolves absolute paths to `config/`, `logs/`, and `exchange/` directories relative to the application root.
* **`ToirApiConfig`** — Jackson DTO mapping the `toir-api` root section of `properties.yaml`, including `data_endpoint`, `equipment_classifier_codes`, `auth_type`, nested authentication blocks (`basic`, `static_token`, `oauth2`), HTTP connection timeouts (`connection`), and exponential backoff retry boundaries (`retry`).
* **`YAMLConfigLoader`** — Loads and validates `config/properties.yaml` using Jackson's `YAMLFactory` with strict *Fail-Fast* error handling.

### Network Layer (`repair_requests.network`)
* **`RR_HttpClient`** — Guice-managed singleton wrapping **Apache HttpClient 5**. Dynamically calculates `DateBegin` and `DateEnd` parameters for the current year (`Europe/Minsk`), synchronizes TLS certificates via `CertUpdater` before `SSLContext` initialization, enforces Preemptive Basic Auth and IIS-compatible headers, caches OAuth2 tokens, executes configurable Exponential Random Backoff retries, and outputs full wire-level HTTP request/response diagnostics.

### Serialization, Domain Models & Persistence (`jackson_json`, `model`, `hibernate`)
* **`RR_DataDeserializer`** — Custom Jackson `StdDeserializer` that performs early metadata-driven filtering against configured `equipment_classifier_codes`, safely parses Russian-keyed 1C timestamps (`dd.MM.yyyy H:mm:ss`), and sanitizes numeric strings without throwing overflow exceptions.
* **`RepairRequests_Data`** — DTO representing a raw repair request object from the upstream 1C:TOiR API.
* **`RepairRequests_Entity` & `RepairRequests_PK`** — JPA entity and `@Embeddable` composite primary key (`Date`, `ApplicationNumber`, `OrganizationCodeMDM`, `DepartmentCodeMDM`) mapped to the target `RepairRequests` table in MS SQL Server.
* **`ExecutionResult`** — DTO modeling the YAML summary payload written to `exchange/result.yaml`.
* **`RepairRequests_DAO`** — Guice singleton managing transactional batch persistence into MS SQL Server with periodic `flush()` and `clear()` calls every 50 records.

### Utilities (`repair_requests.util`)
* **`LogInitializer`** — Sets `javax.net.ssl.trustStore` (`lib/security/cacerts`) and `log.dir` system properties at startup (preserving external `-Dlog.dir` overrides) and forces `Log4j2` context reconfiguration.
* **`ResultFileWriter`** — Serializes `ExecutionResult` metrics (`datetime`, `returned_code`, `records_processed`) into `exchange/result.yaml`.

---

## 📋 Prerequisites

* **Java:** **JDK 17 or higher** *(required by Hibernate 6+, Google Guice 7, Apache HttpClient 5, and Java 14+ Switch Expressions)*
* **Database:** MS SQL Server
* **Build System:** Maven (configured via `pom.xml`)

---

## 💻 Deployment & Running

1. **Prepare Environment Configurations:**
   Copy the `.dist` template files inside `config/` to their active counterparts and update your credentials:
   * `config/properties.yaml.dist` → `config/properties.yaml` (1C:TOiR endpoints, classifier codes, timeouts, auth).
   * `config/Hibernate.cfg.xml.dist` → `config/Hibernate.cfg.xml` (MS SQL JDBC URL via P6Spy and credentials).
   > **Security Note:** Keep production credentials out of version control (both files are ignored via `.gitignore`).

2. **Build the Application:**
   Run Maven to execute JUnit 5 tests, compile the JAR, and assemble dependencies:
   ```bash
   mvn clean package
   ```

3. **Execute the Application:**
   * **Standard Mode:**
     ```bash
     java -jar /path/to/Jackson_RepairRequests-0.0.1-SNAPSHOT.jar
     ```
   * **With Custom Log Directory:** Override default trace destinations (`commons.log` and `p6spy.log`) using `-Dlog.dir`:
     ```bash
     java -Dlog.dir=/var/log/Jackson_RepairRequests -jar /path/to/Jackson_RepairRequests-0.0.1-SNAPSHOT.jar
     ```

---

## 📊 Output & Monitoring

* **Execution Summary (`exchange/result.yaml`):** Automated status report for task schedulers (cron, Windows Task Scheduler, Airflow) containing execution timestamps, return codes (`0` for success, `1` for fatal errors), and processed record counts:
  ```yaml
  datetime: "2026-11-12 15:57:06"
  returned_code: 0
  records_processed: 1
  ```

* **Application & SQL Logs:** Detailed execution traces in `logs/commons.log` (Log4j2) and intercepted JDBC queries with bound parameters in `logs/p6spy.log` (P6Spy).

* **HTTP Wire Diagnostics (`stdout`):** Console output emitted by `RR_HttpClient` displaying outgoing request headers and gateway response codes when troubleshooting 1C/IIS (`wsisapi.dll`) connectivity:
  ```text
  === OUTGOING HTTP REQUEST ===
  Method: GET
  Request URI: /cint/stageTOiR_PS/hs/toir/get?mode=UNFULFILLED&DateBegin=20260101000000&DateEnd=20261231235959
  Target Host: https://your-1c-host:443
  Headers:
    Accept: application/json, text/plain, */*
    User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0
    Host: your-1c-host
    Authorization: Basic <BASE64_ENCODED_CREDENTIALS>
    Accept-Encoding: gzip, x-gzip, deflate
  =============================

  HTTP Response Code: 200
  ```

---

## 🧪 Testing Architecture

The test suite (`src/test/java/repair_requests/`) uses **JUnit 5** and **Mockito** (`MockedStatic`) for isolated unit and integration testing:
* **`RR_DataDeserializerTest`** — Parameterized tests for 1C timestamp parsing, SUID equipment filtering, null handling, and numeric sanitization.
* **`RR_HttpClientTest`** — Validates URI generation, authentication mechanisms, fail-fast configuration, and TLS certificate synchronization.
* **`RR_AppTest`** — Verifies SUID filtering, composite primary key (`RepairRequests_PK`) deduplication, and empty payload handling.

