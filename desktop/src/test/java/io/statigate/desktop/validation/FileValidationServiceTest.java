/*
 * Copyright 2026 The Statigate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.statigate.desktop.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileValidationServiceTest {

    private static final long ONE_MB = 1024L * 1024;
    private final FileValidationService service = new FileValidationService(ONE_MB);

    @TempDir
    Path tempDir;

    @Test
    void rejectsNullFile() {
        var ex = assertThrows(FileValidationException.class, () -> service.validate((java.io.File) null));
        assertTrue(ex.getMessage().toLowerCase().contains("no file"));
    }

    @Test
    void rejectsNullPath() {
        assertThrows(FileValidationException.class, () -> service.validate((Path) null));
    }

    @Test
    void rejectsMissingFile() {
        Path missing = tempDir.resolve("does-not-exist.pdf");
        var ex = assertThrows(FileValidationException.class, () -> service.validate(missing));
        assertTrue(ex.getMessage().contains("Couldn't find"));
    }

    @Test
    void rejectsDirectory() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("a-folder.pdf"));
        var ex = assertThrows(FileValidationException.class, () -> service.validate(dir));
        assertTrue(ex.getMessage().contains("not a file"));
    }

    @Test
    void rejectsUnsupportedExtension() throws IOException {
        Path exe = writeFile("contract.exe", "not really a contract");
        var ex = assertThrows(FileValidationException.class, () -> service.validate(exe));
        assertTrue(ex.getMessage().contains("Unsupported file type"));
        assertTrue(ex.getMessage().contains(".pdf"), "message should list supported types");
    }

    @Test
    void rejectsExtensionlessFile() throws IOException {
        Path noExt = writeFile("README", "hello");
        assertThrows(FileValidationException.class, () -> service.validate(noExt));
    }

    @Test
    void rejectsEmptyFile() throws IOException {
        Path empty = writeFile("empty.txt", "");
        var ex = assertThrows(FileValidationException.class, () -> service.validate(empty));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void rejectsOversizedFile() throws IOException {
        FileValidationService tiny = new FileValidationService(10);
        Path bigish = writeFile("contract.txt", "x".repeat(200));
        var ex = assertThrows(FileValidationException.class, () -> tiny.validate(bigish));
        assertTrue(ex.getMessage().contains("limit"));
    }

    @Test
    void acceptsSupportedExtensionsCaseInsensitively() throws IOException {
        Path upper = writeFile("Contract.PDF", "%PDF-1.4 fake but non-empty");
        ValidatedFile validated = service.validate(upper);
        assertEquals("Contract.PDF", validated.displayName());
        assertTrue(validated.sizeBytes() > 0);
    }

    @Test
    void acceptsTxtAndDocx() throws IOException {
        service.validate(writeFile("a.txt", "clause text"));
        service.validate(writeFile("b.docx", "not a real docx but extension is what's checked"));
    }

    @Test
    void constructorRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new FileValidationService(0));
        assertThrows(IllegalArgumentException.class, () -> new FileValidationService(-1));
    }

    private Path writeFile(String name, String content) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, content);
        return p;
    }
}
