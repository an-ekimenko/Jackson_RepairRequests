package repair_requests.jackson_json;

import java.io.IOException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import repair_requests.model.RepairRequests_Data;

/**
 * Jackson JSON deserializer for {@link RepairRequests_Data} objects.
 */
public class RR_DataDeserializer extends StdDeserializer<RepairRequests_Data> {

    // Reusable formatter instance to avoid recreating it on every deserialization call
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm:ss");

    public RR_DataDeserializer() { 
        this(null); 
    }

    public RR_DataDeserializer(Class<?> vc) { 
        super(vc); 
    }

    @Override 
    public RepairRequests_Data deserialize(JsonParser jp, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        
        JsonNode tree = jp.getCodec().readTree(jp);
        RepairRequests_Data rrData = new RepairRequests_Data();
    	
        // Safe dates extraction (with blank checks)
        // Note: JsonNode.path() never returns null; it returns a MissingNode if the field is absent.
        rrData.setDate(parseDateTime(tree.path("Дата").asText(), DATE_TIME_FORMATTER)); 
        rrData.setDt_from(parseDateTime(tree.path("ДатаНачала").asText(), DATE_TIME_FORMATTER));
        rrData.setDt_to(parseDateTime(tree.path("ДатаОкончания").asText(), DATE_TIME_FORMATTER));
        
        // Safe numbers parsing
        rrData.setApplicationNumber(parseIntSafe(tree.path("Номер").asText()));
        rrData.setOrganizationCodeMDM(parseIntSafe(tree.path("ОрганизацияКодНСИ").asText()));
        rrData.setDepartmentCodeMDM(parseIntSafe(tree.path("ПодразделениеКодНСИ").asText()));
        rrData.setNumberOfOverdueDays(parseIntSafe(tree.path("КоличествоПросроченныхДней").asText()));
        rrData.setRepairFacilityCodeMDM(parseIntSafe(tree.path("ОбъектРемонтаКодНСИ").asText()));

        // Ordinary string processing (returns an empty string "" if the field is empty or missing)
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
        rrData.setClassifierCodeSUID(tree.path("КлассификаторОРКодировкаСУИД").asText());

        return rrData;
    }
    
    // A helper method for safe parsing of LocalDateTime
    private LocalDateTime parseDateTime(String value, DateTimeFormatter formatter) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(value, formatter);
    }
    
    // A helper method for safe parsing of Integer
    private Integer parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null; // Changed from 0 to null for exact state mapping
        }
        
        // Clean the string: retain only digits and an optional leading minus sign
        String numbersOnly = value.replaceAll("[^0-9-]", "");
        
        // Validate if any parseable characters remain after filtering
        if (numbersOnly.isEmpty() || numbersOnly.equals("-")) {
            // Recommendation: Use your configured 'logger.warn' here instead of System.err
            System.err.println("Warning - Unable to extract a number from the string '" + value + "'");
            return null; 
        }
        
        try {
            return Integer.parseInt(numbersOnly);
        } catch (NumberFormatException e) {
            // Handle numeric overflow cases where the value exceeds Integer.MAX_VALUE
            System.err.println("Error - The number '" + numbersOnly + "' goes beyond the Integer range");
            return null;
        }
    }
}
