package cl.duoc.cloudnative.documents;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
public class DocumentController {

    private final PdfGenerator generator;
    private final RestClient storage;

    public DocumentController(PdfGenerator generator,
                              @Value("${storage.service.url:http://localhost:8081}") String url) {
        this.generator = generator;
        this.storage = RestClient.builder().baseUrl(url).build();
    }

    @PostMapping
    public ResponseEntity<Map> create(@Valid @RequestBody CreateDocumentRequest input) throws Exception {
        byte[] pdf = generator.generate(input);
        String filename = "ticket-" + UUID.randomUUID() + ".pdf";

        ByteArrayResource resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", resource);
        form.add("eventoId", input.eventoId());
        form.add("usuarioId", input.usuarioId());

        Map result = storage.post().uri("/internal/storage")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form).retrieve().body(Map.class);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{eventoId}/{usuarioId}/{filename:.+}")
    public ResponseEntity<byte[]> download(@PathVariable String eventoId,
                                           @PathVariable String usuarioId,
                                           @PathVariable String filename) {
        byte[] file = storage.get()
                .uri(b -> b.path("/internal/storage/{eventoId}/{usuarioId}/{filename}")
                        .build(eventoId, usuarioId, filename))
                .retrieve().body(byte[].class);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    @PutMapping("/{eventoId}/{usuarioId}/{filename:.+}")
    public ResponseEntity<Map> update(@PathVariable String eventoId,
                                      @PathVariable String usuarioId,
                                      @PathVariable String filename,
                                      @RequestPart("file") MultipartFile file) throws Exception {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", resource);
        Map result = storage.put()
                .uri(b -> b.path("/internal/storage/{eventoId}/{usuarioId}/{filename}")
                        .build(eventoId, usuarioId, filename))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form).retrieve().body(Map.class);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/list/{eventoId}")
    public List<Map<String, String>> listByEvento(@PathVariable String eventoId) {
        return storage.get()
                .uri("/internal/storage/list/{eventoId}", eventoId)
                .retrieve().body(List.class);
    }
}
