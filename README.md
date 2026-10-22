# Jackson_RepairRequests

**Extracts, parses, and stores pending gas station equipment repair requests from 1C:TOiR into MS SQL staging for SAP BW/4HANA.**

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

## 🔍 Integration Challenges & Solutions

### 1. Anonymous IIS & Stateless 1C Mode
* **Issue:** The target Microsoft IIS 10.0 web server runs with *Anonymous Authentication* enabled. Acting as a transparent proxy, IIS passes raw inbound traffic directly to the `wsisapi.dll` (1C extension module) without performing standard HTTP challenge-response negotiations.
* **Root Cause:** By default, Apache HttpClient 5 issues an initial anonymous request and waits for an HTTP `401 Unauthorized` challenge before transmitting credentials. However, the `wsisapi.dll` module fails to process this negotiation flow under anonymous IIS configs and immediately drops the session on the first unauthenticated packet.
* **Solution:** Implemented **Preemptive Authentication** using `BasicCredentialsProvider`, `AuthCache`, and `HttpClientContext`. This forces the Java client to inject the `Authorization: Basic ...` header into the very first network packet sent over the outbound socket.

### 2. URL UserInfo Deprecation Policy (`ClientProtocolException`)
* **Issue:** Attempting to pass credentials directly within the URL string (`https://domain.by...`) caused the application to crash locally with a `Request URI authority contains deprecated userinfo component` validation exception.
* **Root Cause:** Apache HttpClient 5 strictly enforces the modern **RFC 3986** security specification, inherently blocking raw plain-text credentials embedded inside URL strings to mitigate data leakage risks.
* **Solution:** Decoupled the physical server address from the request structure. The application now extracts and routes the raw execution path via `relativePath`, while wrapping the physical endpoint context inside a dedicated low-level `HttpHost` configuration component.

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

## 🚀 Prerequisites

* **Java:** JDK 8 or higher
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

3. **Execute the Application:** Run the compiled JAR file natively using the Java CLI. Thanks to the automated manifest configuration in `pom.xml`, the classpath linking to the shared `../dependencies/` folder is resolved out of the box, and the internal logging engine dynamically manages its output file storage targets relative to your current execution scope:
   ```bash
   java -jar target/Jackson_RepairRequests-0.0.1-SNAPSHOT.jar
   ```
