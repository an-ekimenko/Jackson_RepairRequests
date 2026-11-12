package repair_requests.jackson_json;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import repair_requests.model.RepairRequests_Data;

/**
 * Jackson JSON deserializer for {@link RepairRequests_Data} objects.
 * Filters records early by configured equipment SUID suffixes.
 */
public class RR_DataDeserializer extends StdDeserializer<RepairRequests_Data> {

    private static final Logger logger = LogManager.getLogger(RR_DataDeserializer.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm:ss");

    private final Set<String> allowedSuidCodes;

    public RR_DataDeserializer() {
        this(List.of("_CF", "_PPS", "_VC"));
    }

    public RR_DataDeserializer(List<String> allowedSuidCodes) {
        super(RepairRequests_Data.class);
        this.allowedSuidCodes = (allowedSuidCodes != null)
                ? allowedSuidCodes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet())
                : Set.of();
    }

    @Override
    public RepairRequests_Data deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode tree = jp.getCodec().readTree(jp);

        // Early SUID filtering before parsing the remaining fields
        String suidCode = tree.path("КлассификаторОРКодировкаСУИД").asText("").trim();
        if (!allowedSuidCodes.isEmpty() && allowedSuidCodes.stream().noneMatch(suidCode::endsWith)) {
            return null;
        }

        RepairRequests_Data rrData = new RepairRequests_Data();
        rrData.setClassifierCodeSUID(suidCode);

        // Safe dates extraction
        rrData.setDate(parseDateTime(tree.path("Дата").asText(), DATE_TIME_FORMATTER));
        rrData.setDt_from(parseDateTime(tree.path("ДатаНачала").asText(), DATE_TIME_FORMATTER));
        rrData.setDt_to(parseDateTime(tree.path("ДатаОкончания").asText(), DATE_TIME_FORMATTER));

        // Safe numbers parsing
        rrData.setApplicationNumber(parseIntSafe(tree.path("Номер").asText()));
        rrData.setOrganizationCodeMDM(parseIntSafe(tree.path("ОрганизацияКодНСИ").asText()));
        rrData.setDepartmentCodeMDM(parseIntSafe(tree.path("ПодразделениеКодНСИ").asText()));
        rrData.setNumberOfOverdueDays(parseIntSafe(tree.path("КоличествоПросроченныхДней").asText()));
        rrData.setRepairFacilityCodeMDM(parseIntSafe(tree.path("ОбъектРемонтаКодНСИ").asText()));

        // Ordinary string processing
        rrData.setApplicationNumber_PON(tree.path("НомерЗаявкиНаПОНе").asText());
        rrData.setOrganization(tree.path("Организация").asText());
        rrData.setDepartment(tree.path("Подразделение").asText());
        rrData.setApplicationType(tree.path("ТипЗаявки").asText());
        rrData.setAuthor(tree.path("Автор").asText());
        rrData.setStatus(tree.path("Статус").asText());
        rrData.setReasonForNon_compliance(tree.path("ПричинаНевыполнения").asText());
        rrData.setRepairFacility(tree.path("ОбъектРемонта").asText());
        rrData.setPurchaseRequest(tree.path("ЗаявкаНаЗакупку").asText());
        rrData.setDescriptionOfRenovation(tree.path("ОписаниеРемонта").asText());

        return rrData;
    }

    private LocalDateTime parseDateTime(String value, DateTimeFormatter formatter) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value, formatter);
    }

    private Integer parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();
        boolean isNegative = trimmed.startsWith("-");
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");

        if (digitsOnly.isEmpty()) {
            logger.warn("Unable to extract a number from the string '{}'", value);
            return null;
        }

        String numbersOnly = isNegative ? "-" + digitsOnly : digitsOnly;

        try {
            return Integer.parseInt(numbersOnly);
        } catch (NumberFormatException e) {
            logger.warn("The number '{}' goes beyond the Integer range", numbersOnly);
            return null;
        }
    }
}
