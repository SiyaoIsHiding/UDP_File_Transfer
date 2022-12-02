import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestByte {
    public static void main(String[] args){
        Path path = Paths.get("received", "data1.txt");
        FileWriter fw = null;
        try {
            fw = new FileWriter(path.toString(), true);
            fw.write("append");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}

