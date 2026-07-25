package com.redactor;

import com.redactor.model.PiiType;
import com.redactor.service.DocxProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Entry point. On startup: reads every .docx in ./input, writes a redacted
 * copy of each to ./output, and prints a per-type count of what was replaced.
 *
 * <p>No command-line arguments needed - just run the jar from the project
 * root with files placed in the input folder.
 */
@SpringBootApplication
public class RedactorApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedactorApplication.class);

    private static final Path INPUT_DIR = Path.of("input");
    private static final Path OUTPUT_DIR = Path.of("output");

    private final DocxProcessor docxProcessor;

    public RedactorApplication(DocxProcessor docxProcessor) {
        this.docxProcessor = docxProcessor;
    }

    public static void main(String[] args) {
        SpringApplication.run(RedactorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Files.createDirectories(INPUT_DIR);
        Files.createDirectories(OUTPUT_DIR);

        List<Path> files;
        try (Stream<Path> listing = Files.list(INPUT_DIR)) {
            files = listing.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".docx"))
                            .sorted()
                            .toList();
        }

        if (files.isEmpty()) {
            log.info("No .docx files found in {}. Place a file there and run again.",
                    INPUT_DIR.toAbsolutePath());
            return;
        }

        for (Path file : files) {
            String outName = file.getFileName().toString().replaceFirst("(?i)\\.docx$", "_redacted.docx");
            Path outFile = OUTPUT_DIR.resolve(outName);

            log.info("Redacting {} -> {}", file.getFileName(), outFile.getFileName());
            try {
                Map<PiiType, Integer> counts = docxProcessor.redact(file, outFile);
                log.info("Done. Replacements by type:");
                counts.forEach((type, n) -> log.info("  {}: {}", type, n));
            } catch (Exception e) {
                log.error("Failed to redact {}: {}", file.getFileName(), e.getMessage(), e);
            }
        }
    }
}
