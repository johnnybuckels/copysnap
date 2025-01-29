package com.github.johannesbuchholz.copysnap.service.diffing.testutils;

import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Assumes unix-like file system.
 */
class TestFileSystemAccessorTest {

    public static Stream<Arguments> ignorePatternsParams() {
        return Stream.of(
                Arguments.of(
                        "*.txt",
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/x/y", "/x/y/blubb.yaml", "other-file.txt"),
                        List.of("other-file.txt")),
                Arguments.of(
                        "*/*.txt",
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt", "/x/y", "/x/y/blubb.yaml", "other-file.txt"),
                        List.of("y/file.txt")),
                Arguments.of(
                        "/*/*.txt",
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt", "/x/y", "/x/y/blubb.yaml", "other-file.txt"),
                        List.of("/y/file.txt")),
                Arguments.of(
                        "**.txt",
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt", "/x/y", "/x/y/blubb.yaml", "other-file.txt"),
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt", "other-file.txt")),
                Arguments.of(
                        "**/*.txt",
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt", "/x/y", "/x/y/blubb.yaml", "other-file.txt"),
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt")),
                Arguments.of(
                        "/**/*.txt",
                        List.of("/x/y/z/file.txt", "y/z/file.txt", "/y/file.txt", "y/file.txt", "/x/y", "/x/y/blubb.yaml", "other-file.txt"),
                        List.of("/x/y/z/file.txt", "/y/file.txt")),
                Arguments.of(
                        "**other*.txt",
                        List.of("other-file.txt", "a/b/other-file.txt"),
                        List.of("other-file.txt", "a/b/other-file.txt"))
        );
    }

    @ParameterizedTest
    @MethodSource("ignorePatternsParams")
    public void ignorePatterns(String globPattern, List<String> paths, List<String> expectedMatches) throws IOException {
        // given
        PathMatcher pathMatcher = FileSystemAccessor.getGlobPathMatcher(globPattern);

        // when
        List<String> actualMatches = paths.stream()
                .map(Path::of)
                .filter(pathMatcher::matches)  // actual test
                .map(Path::toString)
                .toList();

        // then
        assertEquals(expectedMatches, actualMatches);
    }

}