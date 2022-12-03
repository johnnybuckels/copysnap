package johnny.buckels.copysnap.service.diffing.copy;

import java.io.IOException;

@FunctionalInterface
public interface CopyAction {

    void perform() throws IOException;

}
