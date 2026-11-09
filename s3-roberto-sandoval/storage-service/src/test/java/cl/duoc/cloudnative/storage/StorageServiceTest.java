package cl.duoc.cloudnative.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageServiceTest {

    @TempDir
    Path temp;

    @Test
    void rejectsNonPdf() {
        var service = new StorageService(mock(S3Client.class), "bucket", temp.toString());
        var file = new MockMultipartFile("file", "x.txt", "text/plain", "hola".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.store(file, "evento01", "usuario01"));
    }

    @Test
    void rejectsUnsafeEvento() {
        var service = new StorageService(mock(S3Client.class), "bucket", temp.toString());
        var file = new MockMultipartFile("file", "x.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.store(file, "../bad", "usuario01"));
    }
}
