package cl.duoc.cloudnative.storage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/storage")
public class StorageController {

    private final StorageService service;

    public StorageController(StorageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> store(@RequestPart("file") MultipartFile file,
                                     @RequestParam String eventoId,
                                     @RequestParam String usuarioId) throws IOException {
        return service.store(file, eventoId, usuarioId);
    }

    @GetMapping("/{eventoId}/{usuarioId}/{filename:.+}")
    public ResponseEntity<byte[]> download(@PathVariable String eventoId,
                                           @PathVariable String usuarioId,
                                           @PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.load(eventoId, usuarioId, filename));
    }

    @PutMapping(value = "/{eventoId}/{usuarioId}/{filename:.+}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> update(@RequestPart("file") MultipartFile file,
                                      @PathVariable String eventoId,
                                      @PathVariable String usuarioId,
                                      @PathVariable String filename) throws IOException {
        return service.update(file, eventoId, usuarioId, filename);
    }

    @GetMapping("/list/{eventoId}")
    public List<Map<String, String>> listByEvento(@PathVariable String eventoId) {
        return service.listByEvento(eventoId);
    }
}
