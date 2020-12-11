import org.intocps.maestro.Mabl;
import org.intocps.maestro.core.messages.ErrorReporter;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

public class Z3PluginTest {


    @Test
    public void test1() throws Exception {

        File debugOutputFolder = Path.of("target", "test1").toFile();
        debugOutputFolder.mkdirs();
        File specificationFolder = Path.of("src", "test", "resources", "test1").toFile();
        Mabl mabl = new Mabl(specificationFolder, debugOutputFolder);
        mabl.setVerbose(true);
        ErrorReporter reporter = new ErrorReporter();
        mabl.setReporter(reporter);
        mabl.parse(Arrays.asList(new File(specificationFolder, "Z3PluginTest1.mabl")));
        mabl.expand();

        System.out.println(reporter);
    }
}
