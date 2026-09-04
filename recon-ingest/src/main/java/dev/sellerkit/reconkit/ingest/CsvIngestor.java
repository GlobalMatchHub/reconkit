package dev.sellerkit.reconkit.ingest;

import dev.sellerkit.reconkit.domain.enums.EntrySide;
import dev.sellerkit.reconkit.domain.enums.TxnStatus;
import dev.sellerkit.reconkit.domain.model.LedgerEntry;
import dev.sellerkit.reconkit.domain.model.StatementEntry;
import dev.sellerkit.reconkit.domain.model.TransactionEntry;
import dev.sellerkit.reconkit.domain.policy.BusinessDateResolver;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one file into transaction entries.
 *
 * <p>The whole file is hashed before anything is parsed. Re-sending yesterday's file is
 * routine in settlement operations, and a file loaded twice produces a day of duplicates
 * that are indistinguishable from a counterparty double reporting. The hash turns that
 * into a rejected load with a clear reason.
 *
 * <p>Business dates are computed from the timestamp and the counterparty's cutoff, never
 * read from a date column in the file. The file was written by the party being checked.
 */
public final class CsvIngestor {

    public IngestResult ingest(byte[] content,
                               MappingProfile profile,
                               EntrySide side,
                               Long counterpartyId,
                               BusinessDateResolver dateResolver) {
        String hash = sha256(content);
        List<TransactionEntry> entries = new ArrayList<>();
        List<IngestResult.RejectedRow> rejected = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(content), Charset.forName(profile.charset())))) {
            Map<String, Integer> header = null;
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = splitLine(line, profile.delimiter());
                if (lineNumber == 1 && profile.hasHeader()) {
                    header = indexHeader(fields);
                    continue;
                }
                if (header == null) {
                    rejected.add(new IngestResult.RejectedRow(lineNumber, "file has no header row", line));
                    continue;
                }
                try {
                    entries.add(toEntry(fields, header, profile, side, counterpartyId, dateResolver, line));
                } catch (RuntimeException ex) {
                    rejected.add(new IngestResult.RejectedRow(lineNumber, ex.getMessage(), line));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new IngestResult(hash, entries, rejected);
    }

    public IngestResult ingest(InputStream stream, MappingProfile profile, EntrySide side,
                               Long counterpartyId, BusinessDateResolver dateResolver) {
        try {
            return ingest(stream.readAllBytes(), profile, side, counterpartyId, dateResolver);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private TransactionEntry toEntry(List<String> fields, Map<String, Integer> header,
                                     MappingProfile profile, EntrySide side, Long counterpartyId,
                                     BusinessDateResolver dateResolver, String rawLine) {
        TransactionEntry entry = side == EntrySide.LEDGER ? new LedgerEntry() : new StatementEntry();
        entry.setCounterpartyId(counterpartyId);
        entry.setRawLine(rawLine);

        String currency = value(fields, header, profile.currencyColumn());
        if (currency == null || currency.isBlank()) {
            currency = profile.defaultCurrency();
        }
        entry.setCurrency(currency);

        String timestamp = require(fields, header, profile.timestampColumn());
        Instant occurredAt = LocalDateTime
                .parse(timestamp.trim(), DateTimeFormatter.ofPattern(profile.timestampFormat()))
                .atZone(dateResolver.zone())
                .toInstant();
        entry.setOccurredAt(occurredAt);
        entry.setBusinessDate(dateResolver.resolve(occurredAt));

        entry.setExternalTxnId(value(fields, header, profile.externalTxnIdColumn()));
        entry.setApprovalNo(value(fields, header, profile.approvalNoColumn()));
        entry.setOrderId(value(fields, header, profile.orderIdColumn()));
        entry.setMerchantNo(value(fields, header, profile.merchantNoColumn()));
        entry.setPaymentMethod(value(fields, header, profile.paymentMethodColumn()));
        entry.setCardBin(value(fields, header, profile.cardBinColumn()));

        entry.setGrossMinorUnits(AmountParser.toMinorUnits(
                require(fields, header, profile.grossColumn()), currency));
        entry.setFeeMinorUnits(AmountParser.toMinorUnits(
                value(fields, header, profile.feeColumn()), currency));
        String net = value(fields, header, profile.netColumn());
        entry.setNetMinorUnits(net == null || net.isBlank()
                ? entry.getGrossMinorUnits() - entry.getFeeMinorUnits()
                : AmountParser.toMinorUnits(net, currency));

        String status = require(fields, header, profile.statusColumn()).trim();
        TxnStatus mapped = profile.statusMap().get(status);
        if (mapped == null) {
            throw new IllegalArgumentException(
                    "unknown status '%s' for profile %s".formatted(status, profile.name()));
        }
        entry.setTxnStatus(mapped);

        // Settlement files state amounts as magnitudes and put the direction in the type
        // column: a refund of 50,000 with a fee of 1,206 is written exactly like a sale.
        // A refund gives the fee back, so the stored fee is signed to match the direction
        // of the transaction. Leaving it positive makes the engine compare a returned fee
        // against a charged one and report every refund in the file as a fee dispute.
        if (entry.isReversal()) {
            entry.setFeeMinorUnits(-Math.abs(entry.getFeeMinorUnits()));
            entry.setNetMinorUnits(-Math.abs(entry.getNetMinorUnits()));
        }
        return entry;
    }

    private static Map<String, Integer> indexHeader(List<String> fields) {
        Map<String, Integer> index = new HashMap<>();
        for (int position = 0; position < fields.size(); position++) {
            index.put(stripBom(fields.get(position)).trim(), position);
        }
        return index;
    }

    private static String value(List<String> fields, Map<String, Integer> header, String column) {
        if (column == null) {
            return null;
        }
        Integer position = header.get(column);
        if (position == null || position >= fields.size()) {
            return null;
        }
        String raw = fields.get(position).trim();
        return raw.isEmpty() ? null : raw;
    }

    private static String require(List<String> fields, Map<String, Integer> header, String column) {
        String found = value(fields, header, column);
        if (found == null) {
            throw new IllegalArgumentException("required column '" + column + "' is missing or empty");
        }
        return found;
    }

    /** Minimal RFC 4180 handling: quoted fields, doubled quotes inside them. */
    private static List<String> splitLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        current.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == delimiter) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String stripBom(String value) {
        return value.startsWith("﻿") ? value.substring(1) : value;
    }

    public static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
