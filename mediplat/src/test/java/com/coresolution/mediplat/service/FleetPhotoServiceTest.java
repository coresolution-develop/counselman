package com.coresolution.mediplat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class FleetPhotoServiceTest {

    @Test
    void store_savesFileUnderInstDirAndReturnsRelativePath(@TempDir Path tempDir) throws IOException {
        FleetPhotoService service = newService(tempDir);
        MockMultipartFile file = new MockMultipartFile("photo", "dash.jpg", "image/jpeg", new byte[] {1, 2, 3});

        String relativePath = service.store("core", file);

        assertNotNull(relativePath);
        assertTrue(relativePath.startsWith("core/"), "inst 하위에 저장되어야 한다: " + relativePath);
        assertTrue(Files.exists(tempDir.resolve(relativePath)), "실제 파일이 존재해야 한다");
    }

    @Test
    void store_rejectsNonImage(@TempDir Path tempDir) {
        FleetPhotoService service = newService(tempDir);
        MockMultipartFile file = new MockMultipartFile("photo", "note.txt", "text/plain", new byte[] {1});

        assertThrows(IllegalArgumentException.class, () -> service.store("core", file));
    }

    @Test
    void store_rejectsEmptyFile(@TempDir Path tempDir) {
        FleetPhotoService service = newService(tempDir);
        MockMultipartFile file = new MockMultipartFile("photo", "dash.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> service.store("core", file));
    }

    @Test
    void delete_removesStoredFile(@TempDir Path tempDir) {
        FleetPhotoService service = newService(tempDir);
        String relativePath = service.store("core",
                new MockMultipartFile("photo", "dash.jpg", "image/jpeg", new byte[] {1, 2, 3}));

        assertTrue(service.delete(relativePath));
        assertFalse(Files.exists(tempDir.resolve(relativePath)));
    }

    @Test
    void delete_traversalPathIsRejectedSafely(@TempDir Path tempDir) {
        FleetPhotoService service = newService(tempDir);

        assertFalse(service.delete("../../etc/passwd"));
    }

    private FleetPhotoService newService(Path baseDir) {
        FleetPhotoService service = new FleetPhotoService();
        ReflectionTestUtils.setField(service, "baseDir", baseDir.toString());
        return service;
    }
}
