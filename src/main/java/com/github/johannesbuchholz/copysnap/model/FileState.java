package com.github.johannesbuchholz.copysnap.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record FileState(Path path, Instant lastModified, CheckpointChecksum checksum) {

    private static final String FIELD_SERDE_SEPARATOR = ";";
    private static final Pattern FILE_STATE_STRING_PATTERN = Pattern.compile("^(?<checksum>.+);(?<modified>.+);(?<path>(?s).+)$");

    public static FileState deserialize(String fileStateString) {
        Matcher matcher = FILE_STATE_STRING_PATTERN.matcher(fileStateString);
        if (!matcher.find())
            throw new IllegalArgumentException("Could not create file state from string %s: Does not match pattern %s".formatted(fileStateString, FILE_STATE_STRING_PATTERN));
        CheckpointChecksum checksum = CheckpointChecksum.deserialize(matcher.group("checksum"));
        Instant modified = Instant.parse(matcher.group("modified"));
        Path path = Path.of(matcher.group("path"));
        return new FileState(path, modified, checksum);
    }

    public static FileState readFileState(Path rootToRelativizeAgainst, Path absPath) throws UncheckedIOException {
        Instant lastModified;
        try {
            lastModified = Files.getLastModifiedTime(absPath).toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read last modified from %s: %s".formatted(absPath, e.getMessage()), e);
        }
        CheckpointChecksum checksum;
        try {
            checksum = CheckpointChecksum.from(Files.newInputStream(absPath));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create checksum from %s: %s".formatted(absPath, e.getMessage()), e);
        }
        return new FileState(rootToRelativizeAgainst.relativize(absPath), lastModified, checksum);
    }

    public FileState {
        if (path.isAbsolute())
            throw new IllegalArgumentException("Given path is not relative: " + path);
    }

    public Path getPath() {
        return path;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public CheckpointChecksum getChecksum() {
        return checksum;
    }

    public String serialize() {
        return String.join(FIELD_SERDE_SEPARATOR, List.of(checksum.serialize(), lastModified.toString(), path.toString()));
    }

}
