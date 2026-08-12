package com.example.shoonyamonitor.shoonya;

import com.example.shoonyamonitor.config.ShoonyaProperties;
import com.example.shoonyamonitor.model.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads, caches and parses the Shoonya NSE symbol master, producing the
 * full list of tradable EQ-series equities.
 *
 * <p>The symbol master is a zipped CSV published at
 * {@code https://api.shoonya.com/NSE_symbols.txt.zip} with the header
 * {@code Exchange,Token,LotSize,Symbol,TradingSymbol,Instrument,TickSize}.
 * Only rows whose {@code Instrument} column equals {@code EQ} are kept.</p>
 *
 * <p>After a successful download the raw text is written to a cache file so a
 * later start can fall back to it if the download fails.</p>
 */
@Component
public class SymbolMasterService {

    private static final Logger log = LoggerFactory.getLogger(SymbolMasterService.class);

    /** Column index of the instrument type in the CSV (0-based). */
    private static final int COL_EXCHANGE = 0;
    private static final int COL_TOKEN = 1;
    private static final int COL_SYMBOL = 3;
    private static final int COL_INSTRUMENT = 5;
    private static final String CACHE_FILE = "NSE_symbols.txt";

    private final ShoonyaProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public SymbolMasterService(ShoonyaProperties props) {
        this.props = props;
    }

    /**
     * Loads the EQ-series instrument universe. Tries a fresh download first and
     * falls back to the cached copy on failure. Returns an empty list only when
     * both the download and the cache are unavailable.
     */
    public List<Instrument> loadUniverse() {
        String raw = download();
        if (raw != null) {
            writeCache(raw);
        } else {
            log.warn("Symbol master download failed; attempting cached copy.");
            raw = readCache();
        }
        if (raw == null || raw.isBlank()) {
            log.error("No symbol master available (download failed and no cache). "
                    + "Universe will be empty.");
            return List.of();
        }
        return parseEquities(raw);
    }

    /** Downloads and unzips the symbol master, returning the raw CSV text. */
    private String download() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getSymbolMasterUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response =
                    http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Symbol master download returned HTTP {}", response.statusCode());
                return null;
            }
            return unzipFirstEntry(response.body());
        } catch (Exception e) {
            log.warn("Symbol master download error: {}", e.getMessage());
            return null;
        }
    }

    /** Extracts the first entry of a zip archive as UTF-8 text. */
    private static String unzipFirstEntry(byte[] zipped) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipped))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                return null;
            }
            return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parses the CSV text into EQ-series instruments. The header line is
     * skipped, malformed rows are counted and skipped, and only rows whose
     * instrument column equals {@code EQ} are returned.
     */
    private List<Instrument> parseEquities(String csv) {
        List<Instrument> out = new ArrayList<>();
        int parseErrors = 0;
        boolean header = true;
        for (String line : csv.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (header) {
                header = false; // first non-blank line is the header
                continue;
            }
            String[] c = line.split(",");
            if (c.length <= COL_INSTRUMENT) {
                parseErrors++;
                continue;
            }
            String instrument = c[COL_INSTRUMENT].trim();
            if (!"EQ".equalsIgnoreCase(instrument)) {
                continue;
            }
            String exchange = c[COL_EXCHANGE].trim();
            String token = c[COL_TOKEN].trim();
            String symbol = c[COL_SYMBOL].trim();
            if (exchange.isEmpty() || token.isEmpty()) {
                parseErrors++;
                continue;
            }
            String name = symbol.isEmpty() ? token : symbol;
            out.add(new Instrument(exchange, token, name, false));
        }
        log.info("Symbol master parsed: {} EQ instruments ({} rows skipped)",
                out.size(), parseErrors);
        return out;
    }

    private Path cacheFile() {
        String dir = props.getSymbolMasterCache();
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("java.io.tmpdir");
        }
        return Paths.get(dir, CACHE_FILE);
    }

    private void writeCache(String raw) {
        try {
            Path f = cacheFile();
            Files.createDirectories(f.getParent());
            Files.writeString(f, raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Could not write symbol master cache: {}", e.getMessage());
        }
    }

    private String readCache() {
        try {
            Path f = cacheFile();
            if (Files.exists(f)) {
                log.info("Using cached symbol master at {}", f);
                return Files.readString(f, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("Could not read symbol master cache: {}", e.getMessage());
        }
        return null;
    }
}
