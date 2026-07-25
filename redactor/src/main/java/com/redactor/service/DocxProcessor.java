package com.redactor.service;

import com.redactor.detect.EntityGazetteer;
import com.redactor.model.PiiMatch;
import com.redactor.model.PiiType;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads a .docx, redacts PII everywhere in it (body, tables, nested tables,
 * headers, footers), and writes a new .docx. The input file is never modified.
 */
@Service
public class DocxProcessor {

    private final RedactionService redactionService;
    private final EntityGazetteer gazetteer;

    public DocxProcessor(RedactionService redactionService, EntityGazetteer gazetteer) {
        this.redactionService = redactionService;
        this.gazetteer = gazetteer;
    }

    /** @return replacement counts by PII type, for a quick summary in the console. */
    public Map<PiiType, Integer> redact(Path input, Path output) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(input))) {

            // Pass 1 (read-only): learn every person/company name the document
            // introduces about itself, so Pass 2 can catch them anywhere in the
            // document, not just next to the sentence that first named them.
            StringBuilder fullText = new StringBuilder();
            forEachParagraph(doc, p -> fullText.append(p.getText()).append('\n'));
            gazetteer.prime(fullText.toString());

            // Pass 2: redact every paragraph in place.
            Map<PiiType, Integer> counts = new EnumMap<>(PiiType.class);
            forEachParagraph(doc, p -> redactParagraph(p, counts));

            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(output)) {
                doc.write(out);
            }
            return counts;
        }
    }

    /** Visits every paragraph in the document body plus all headers and footers. */
    private void forEachParagraph(XWPFDocument doc, Consumer<XWPFParagraph> visitor) {
        visitBody(doc, visitor);
        for (XWPFHeader header : doc.getHeaderList()) {
            visitBody(header, visitor);
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            visitBody(footer, visitor);
        }
    }

    /**
     * Visits every paragraph directly in {@code body}, then walks into its
     * tables. A table cell is itself an IBody, so this same method also
     * covers tables nested inside tables with no extra code.
     */
    private void visitBody(IBody body, Consumer<XWPFParagraph> visitor) {
        for (XWPFParagraph p : body.getParagraphs()) {
            visitor.accept(p);
        }
        for (XWPFTable table : body.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    visitBody(cell, visitor);
                }
            }
        }
    }

    /**
     * Word can silently split one sentence across several runs (its unit of
     * formatting) from editing history or spell-check, so a name could
     * straddle a split invisibly. To handle that safely: read the whole
     * paragraph as one string, detect and replace on that string, then write
     * the result into the first run and clear the rest.
     *
     * <p>Trade-off, stated plainly: if the original paragraph mixed formatting
     * (part bold, part not), the redacted text takes on only the first run's
     * formatting throughout. See the README.
     */
    private void redactParagraph(XWPFParagraph paragraph, Map<PiiType, Integer> counts) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return;
        }

        StringBuilder original = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) {
                original.append(text);
            }
        }
        if (original.length() == 0) {
            return;
        }

        List<PiiMatch> matches = redactionService.findAll(original.toString());
        if (matches.isEmpty()) {
            return;
        }

        StringBuilder redacted = new StringBuilder();
        int cursor = 0;
        for (PiiMatch match : matches) {
            redacted.append(original, cursor, match.start());
            redacted.append(redactionService.pseudonymFor(match.type(), match.value()));
            cursor = match.end();
            counts.merge(match.type(), 1, Integer::sum);
        }
        redacted.append(original, cursor, original.length());

        runs.get(0).setText(redacted.toString(), 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }
}
