package com.github.johannesbuchholz.copysnap.service.diffing.copy;

import com.github.johannesbuchholz.copysnap.model.state.FileState;
import com.github.johannesbuchholz.copysnap.service.diffing.FileSystemAccessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractCopyActionTest {
    
    private static final Path A = Path.of("a");
    private static final Path B = Path.of("b");
    private static final Path C = Path.of("c");
    private static final Path X = Path.of("x");

    private static class TestCa extends AbstractCopyAction {

        protected TestCa(Path sourceRootLocation, Path destinationRootLocation, Path relPath) {
            super(sourceRootLocation, destinationRootLocation, relPath);
        }

        @Override
        public Optional<FileState> perform(FileSystemAccessor fsa) {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "[%s]".formatted(String.join(", ", List.of(relPath.toString(), sourceRootLocation.toString(), destinationRootLocation.toString())));
        }
    }

    @Test
    void sortTest_relPart() {
        AbstractCopyAction aca1 = new TestCa(A, A, C);
        AbstractCopyAction aca2 = new TestCa(A, A, A);

        List<? extends CopyAction> actual = Set.of(aca1, aca2).stream().sorted().toList();
        List<CopyAction> expected = List.of(aca2, aca1);

        assertEquals(expected, actual);
    }

    @Test
    void sortTest_sourcePart() {
        AbstractCopyAction aca1 = new TestCa(C, A, A);
        AbstractCopyAction aca2 = new TestCa(A, A, A);

        List<? extends CopyAction> actual = Set.of(aca1, aca2).stream().sorted().toList();
        List<CopyAction> expected = List.of(aca2, aca1);

        assertEquals(expected, actual);
    }

    @Test
    void sortTest_destPart() {
        AbstractCopyAction aca1 = new TestCa(A, C, A);
        AbstractCopyAction aca2 = new TestCa(A, A, A);

        List<? extends CopyAction> actual = Set.of(aca1, aca2).stream().sorted().toList();
        List<CopyAction> expected = List.of(aca2, aca1);

        assertEquals(expected, actual);
    }

    @Test
    void sortTest_orderOfComparisonIs_rel_source_dest() {
        CopyAction aca1 = new TestCa(X, X, C);
        CopyAction aca2 = new TestCa(X, X, B);
        CopyAction aca3 = new TestCa(X, X, A);

        CopyAction aca4 = new TestCa(X, C, C);
        CopyAction aca5 = new TestCa(X, B, B);
        CopyAction aca6 = new TestCa(X, A, A);

        CopyAction aca7 = new TestCa(C, C, C);
        CopyAction aca8 = new TestCa(B, B, B);
        CopyAction aca9 = new TestCa(A, A, A);

        List<CopyAction> actual = Stream.of(aca1, aca2, aca3, aca4, aca5, aca6, aca7, aca8, aca9).sorted().toList();
        List<CopyAction> expected = List.of(aca9, aca6, aca3, aca8, aca5, aca2, aca7, aca4, aca1);

        assertEquals(expected, actual);
    }

}