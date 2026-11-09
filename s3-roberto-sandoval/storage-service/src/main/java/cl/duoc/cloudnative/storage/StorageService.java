package cl.duoc.cloudnative.storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class StorageService {

    private final S3Client s3;
    private final String bucket;
    private final Path temporary;

    public StorageService(S3Client s3,
                          @Value("${storage.s3.bucket}") String bucket,
                          @Value("${storage.efs.path:/mnt/efs}") String path) {
        this.s3 = s3;
        this.bucket = bucket;
        this.temporary = Path.of(path).toAbsolutePath();
    }

    public Map<String, String> store(MultipartFile file, String eventoId, String usuarioId) throws IOException {
        checkId(eventoId, "Evento inválido");
        checkId(usuarioId, "Usuario inválido");

        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF vacío o demasiado grande");
        }

        byte[] header = file.getInputStream().readNBytes(5);
        if (!Arrays.equals(header, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere PDF");
        }

        Files.createDirectories(temporary);
        String name = "ticket-" + UUID.randomUUID() + ".pdf";
        String key = "eventos/" + eventoId + "/usuarios/" + usuarioId + "/" + name;

        Path path = Files.createTempFile(temporary, "ticket-", ".pdf");
        try {
            file.transferTo(path);
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/pdf").build(),
                    RequestBody.fromFile(path));
        } finally {
            Files.deleteIfExists(path);
        }

        return Map.of(
                "eventoId", eventoId,
                "usuarioId", usuarioId,
                "filename", name,
                "s3Key", key,
                "storage", "S3",
                "temporaryStorage", "EFS"
        );
    }

    public byte[] load(String eventoId, String usuarioId, String filename) {
        checkId(eventoId, "Evento inválido");
        checkId(usuarioId, "Usuario inválido");

        if (!filename.matches("ticket-[a-f0-9-]{36}\\.pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre inválido");
        }

        String key = "eventos/" + eventoId + "/usuarios/" + usuarioId + "/" + filename;
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado");
        }
    }

    public List<Map<String, String>> listByEvento(String eventoId) {
        checkId(eventoId, "Evento inválido");

        String prefix = "eventos/" + eventoId + "/";
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        ListObjectsV2Response response = s3.listObjectsV2(request);

        return response.contents().stream().map(obj -> {
            String key = obj.key();
            String[] parts = key.split("/");
            String usuarioId = parts.length > 3 ? parts[3] : "desconocido";
            String filename = parts.length > 4 ? parts[4] : "desconocido";
            return Map.of(
                    "s3Key", key,
                    "eventoId", eventoId,
                    "usuarioId", usuarioId,
                    "filename", filename,
                    "size", String.valueOf(obj.size())
            );
        }).collect(Collectors.toList());
    }

    public Map<String, String> update(MultipartFile file, String eventoId, String usuarioId, String filename) throws IOException {
        checkId(eventoId, "Evento inválido");
        checkId(usuarioId, "Usuario inválido");
        if (!filename.matches("ticket-[a-f0-9-]{36}\\.pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre inválido");
        }
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF vacío o demasiado grande");
        }
        byte[] header = file.getInputStream().readNBytes(5);
        if (!Arrays.equals(header, new byte[]{x27%x27, x27Px27, x27Dx27, x27Fx27, x27-x27})) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere PDF");
        }
        String key = "eventos/" + eventoId + "/usuarios/" + usuarioId + "/" + filename;
        Path path = Files.createTempFile(temporary, "ticket-", ".pdf");
        try {
            file.transferTo(path);
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/pdf").build(),
                    RequestBody.fromFile(path));
        } finally {
            Files.deleteIfExists(path);
        }
        return Map.of(
                "eventoId", eventoId,
                "usuarioId", usuarioId,
                "filename", filename,
                "s3Key", key,
                "storage", "S3",
                "temporaryStorage", "EFS"
        );
    }

    private void checkId(String id, String message) {
        if (!id.matches("[A-Za-z0-9_-]{1,50}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
