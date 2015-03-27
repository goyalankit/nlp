import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        File[] files = new File[1];
        for (int i = 0; i < files.length; i++)
            files[i] = new File(args[i]);

        boolean suffixSwitch = args[1].toString().equals("true") ? true : false;
        boolean morph = args[2].toString().equals("true") ? true : false;
        System.out.println("SuffixSwitch " + suffixSwitch + " Morph " + morph );
        // Get list of sentences from the tagged input files
        List<List<String>> sentences = POSTaggedFile.convertToTokenLists(files, suffixSwitch, morph);
        int numSentences = sentences.size();

        // Take training sentences from start of data
        List<List<String>> trainSentences = sentences.subList(0, numSentences);
        PrintWriter writer = new PrintWriter(new FileWriter(args[0] + args[3].toString()));

        for (List<String> sentence : sentences) {
            for (String str : sentence) {
                writer.print(str);
            }
            writer.println();
        }
        writer.close();
    }

    public static int wordCount(List<List<String>> sentences) {
        int wordCount = 0;
        for (List<String> sentence : sentences) {
            wordCount += sentence.size();
        }
        return wordCount;
    }
}
