package com.chronos.service;

import com.chronos.dto.internal.AnomalyLine;
import com.chronos.dto.internal.ReportLine;
import com.chronos.dto.internal.ReportResult;
import com.chronos.exception.ChronosException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates the two CSV output files from a ReportResult.
 *
 * Report CSV headers match the expected column layout for the financial team.
 * Anomalies CSV includes enough context for data engineers to identify and
 * fix the underlying issue.
 *
 * Both methods return byte[] — the controller writes them as downloadable
 * file responses. Nothing is persisted to disk by this service.
 */
@Slf4j
@Service
public class CsvExportService {

    // ── Report CSV ────────────────────────────────────────────────────────────

    private static final String[] REPORT_HEADERS = {
        "Year",
        "Month",
        "Employee Identifier",
        "First Name",
        "Last Name",
        "Registration Number",
        "Company",
        "Organizational Unit",
        "Parent OU",
        "Product",
        "Activity Nature",
        "Accounting Code",
        "Man Days",
        "Ratio",
        "Prefix"
    };

    private static final String[] ANOMALY_HEADERS = {
        "Year",
        "Month",
        "Employee Identifier",
        "First Name",
        "Last Name",
        "Registration Number",
        "Company",
        "Missing Field",
        "Issue"
    };

    /**
     * Generates the Report CSV as a UTF-8 byte array.
     * Prefix column contains "NO_TS_" for default-allocation rows,
     * empty string for timesheet-derived rows.
     */
    public byte[] generateReportCsv(List<ReportLine> lines) {
        log.info("Generating Report CSV — {} lines", lines.size());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(REPORT_HEADERS)
                             .build())) {

            for (ReportLine line : lines) {
                printer.printRecord(
                    line.getYear(),
                    line.getMonth(),
                    line.getEmployeeIdentifier(),
                    line.getFirstName(),
                    line.getLastName(),
                    nullToEmpty(line.getRegistrationNumber()),
                    nullToEmpty(line.getCompanyName()),
                    nullToEmpty(line.getOuName()),
                    nullToEmpty(line.getParentOuName()),
                    nullToEmpty(line.getProductName()),
                    nullToEmpty(line.getActivityNatureName()),
                    nullToEmpty(line.getAccountingCodeIdentifier()),
                    line.getManDays(),
                    line.getRatio(),
                    nullToEmpty(line.getPrefix())
                );
            }

            printer.flush();
            return out.toByteArray();

        } catch (IOException e) {
            throw new ChronosException("Failed to generate Report CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the Anomalies CSV as a UTF-8 byte array.
     * Returns an empty CSV (headers only) when there are no anomalies.
     */
    public byte[] generateAnomaliesCsv(List<AnomalyLine> lines) {
        log.info("Generating Anomalies CSV — {} lines", lines.size());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(ANOMALY_HEADERS)
                             .build())) {

            for (AnomalyLine line : lines) {
                printer.printRecord(
                    line.getYear(),
                    line.getMonth(),
                    line.getEmployeeIdentifier(),
                    line.getFirstName(),
                    line.getLastName(),
                    nullToEmpty(line.getRegistrationNumber()),
                    nullToEmpty(line.getCompanyName()),
                    nullToEmpty(line.getMissingField()),
                    nullToEmpty(line.getIssue())
                );
            }

            printer.flush();
            return out.toByteArray();

        } catch (IOException e) {
            throw new ChronosException("Failed to generate Anomalies CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method — generates both files in one call.
     * Returns a two-element array: [reportCsvBytes, anomaliesCsvBytes].
     */
    public byte[][] generateBoth(ReportResult result) {
        return new byte[][] {
            generateReportCsv(result.getReportLines()),
            generateAnomaliesCsv(result.getAnomalyLines())
        };
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
