package repair_requests;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import repair_requests.hibernate.RepairRequests_DAO;
import repair_requests.model.RepairRequests_Data;
import repair_requests.model.RepairRequests_Entity;
import repair_requests.model.RepairRequests_PK;
import repair_requests.network.RR_HttpClient;

/**
 * Main application orchestrator coordinating the end-to-end ETL data pipeline.
 */
public class RR_App {

    private static final Logger logger = LogManager.getLogger(RR_App.class);

    // Critical gas station equipment SUID suffixes: Coffee machines, Tire inflation, Vacuum cleaners
    private static final Set<String> ALLOWED_SUID_CODES = Set.of("_CF", "_PPS", "_VC");

    private final RR_HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RepairRequests_DAO dao;

    @Inject
    public RR_App(RR_HttpClient httpClient, ObjectMapper objectMapper, RepairRequests_DAO dao) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.dao = dao;
    }

    /**
     * Executes the full ETL cycle: fetch -> deserialize -> filter -> deduplicate -> persist.
     *
     * @return number of processed and persisted entity records
     * @throws Exception if any stage of the pipeline fails
     */
    public int run() throws Exception {
        logger.info("Starting RepairRequests ETL pipeline execution...");

        String rawJson = httpClient.fetchRepairRequestsJson();
        if (rawJson == null || rawJson.isBlank()) {
            logger.warn("Received empty JSON payload from TOiR API. Skipping database update.");
            return 0;
        }

        List<RepairRequests_Data> rawDataList = objectMapper.readValue(
                rawJson,
                new TypeReference<List<RepairRequests_Data>>() {}
        );

        logger.info("Deserialized {} raw repair request records from JSON.", rawDataList.size());

        // Filter by critical SUID codes, map to JPA entities, and deduplicate by composite PK
        List<RepairRequests_Entity> entitiesToSave = rawDataList.stream()
                .filter(this::isAllowedEquipment)
                .map(this::mapToEntity)
                .filter(entity -> entity.getId() != null)
                .collect(Collectors.toMap(
                        RepairRequests_Entity::getId,
                        Function.identity(),
                        (existing, replacement) -> replacement
                ))
                .values()
                .stream()
                .toList();

        logger.info("Filtered and deduplicated {} target entities for persistence.", entitiesToSave.size());

        if (entitiesToSave.isEmpty()) {
            logger.info("No matching repair requests found after filtering. Database write skipped.");
            return 0;
        }

        dao.saveOrUpdateBatch(entitiesToSave);
        return entitiesToSave.size();
    }

    private boolean isAllowedEquipment(RepairRequests_Data data) {
        if (data == null || data.getClassifierCodeSUID() == null) {
            return false;
        }
        String code = data.getClassifierCodeSUID().trim();
        return ALLOWED_SUID_CODES.stream().anyMatch(code::endsWith);
    }

    private RepairRequests_Entity mapToEntity(RepairRequests_Data data) {
        RepairRequests_PK pk = new RepairRequests_PK(
                data.getDate(),
                data.getApplicationNumber(),
                data.getOrganizationCodeMDM(),
                data.getDepartmentCodeMDM()
        );

        return new RepairRequests_Entity(
                pk,
                data.getDt_from(),
                data.getDt_to(),
                data.getApplicationNumber_PON(),
                data.getApplicationType(),
                data.getAuthor(),
                data.getStatus(),
                data.getNumberOfOverdueDays(),
                data.getReasonForNon_compliance(),
                data.getRepairFacility(),
                data.getRepairFacilityCodeMDM(),
                data.getPurchaseRequest(),
                data.getDescriptionOfRenovation()
        );
    }
}
