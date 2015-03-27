import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class POSTaggedFile {

    /**
     * The name of the LDC POS file
     */
    public File file = null;
    static public boolean enableMorphGeneration = false;
    static public boolean enableSuffixGeneration = false;

    /**
     * The I/O reader for accessing the file
     */
    protected BufferedReader reader = null;

    /**
     * Create an object for a given LDC POS tagged file
     */
    public POSTaggedFile(File file) {
        this.file = file;
        try {
            this.reader = new BufferedReader(new FileReader(file));
        } catch (IOException e) {
            System.out.println("\nCould not open POSTaggedFile: " + file);
            System.exit(1);
        }
    }

    /**
     * Return the next line of POS tagged tokens from this file.
     * Returns "\n" if end of sentence and start of a new one.
     * Returns null if end of file
     */
    protected String getNextPOSLine() {
        String line = null;
        try {
            do {
                // Read a line from the file
                line = reader.readLine();
                if (line == null) {
                    // End of file, no more tokens, return null
                    reader.close();
                    return null;
                }
                // Sentence boundary indicator
                if (line.startsWith("======="))
                    line = "\n";
                // If sentence number indicator for ATIS or comment for Brown, ignore it
                if (line.startsWith("[ @") || line.startsWith("*x*"))
                    line = "";
            } while (line.equals(""));
        } catch (IOException e) {
            System.out.println("\nCould not read from TextFileDocument: " + file);
            System.exit(1);
        }
        return line;
    }

    /**
     * Take a line from the file and return a list of String tokens in the line
     */
    protected List<String> getTokens(String line) {
        List<String> tokenList = new ArrayList<String>();
        line = line.trim();
        // Use a tokenizer to extract token/POS pairs in line,
        // ignore brackets indicating chunk boundaries
        StringTokenizer tokenizer = new StringTokenizer(line, " []");
        while (tokenizer.hasMoreTokens()) {
            String tokenPos = tokenizer.nextToken();
            tokenList.add(segmentToken(tokenPos));

            // If last token in line has end of sentence tag ".",
            // add a sentence end token </S>
            if (tokenPos.endsWith("/.") && !tokenizer.hasMoreTokens()) {
                tokenList.add("</S>");
            }
        }
        return tokenList;
    }

    /**
     * Segment a token/POS string and return just the token
     */
    protected String segmentToken(String tokenPos) {
        // POS tag follows the last slash
        int slash = tokenPos.lastIndexOf("/");
        if (slash < 0)
            return tokenPos;
        else {

            String str = tokenPos.substring(0, slash);
            String output = str;
            if (enableMorphGeneration) {
                if (Character.isUpperCase(str.charAt(0))) {
                    output = output + " caps";
                }
                if (str.contains("-")) {
                    output = output + " hyphen";
                }
                if(str.matches(".*\\d.*")){
                    output = output + " num";
                }
            }
            if (enableSuffixGeneration) {
                //Cover about 85% of suffixes
                if (str.endsWith("able")) {
                    output = output + " able";
                }
                if (str.endsWith("ed")) {
                    output = output + " ed";
                }
                if (str.endsWith("er")) {
                    output = output + " er";
                }
                if (str.endsWith("ly")) {
                    output = output + " ly";
                }
                if (str.endsWith("ing")) {
                    output = output + " ing";
                }
                if (str.endsWith("ful")) {
                    output = output + " ful";
                }
                if (str.endsWith("less")) {
                    output = output + " less";
                } else if (str.endsWith("s") || str.endsWith("es")) {
                    output = output + " es";
                }
                if (str.endsWith("ity")) {
                    output = output + " ity";
                }
                if (str.endsWith("or")) {
                    output = output + " or";
                }
                if (str.endsWith("ion")) {
                    output = output + " ion";
                }
            }
            output = output + " " + tokenPos.substring(slash + 1, tokenPos.length());
            output = output + System.getProperty("line.separator");

            return output;
        }
    }

    /**
     * Return a List of sentences each represented as a List of String tokens for
     * the sentences in this file
     */
    protected List<List<String>> tokenLists() {
        List<List<String>> sentences = new ArrayList<List<String>>();
        List<String> sentence = new ArrayList<String>();
        String line;
        while ((line = getNextPOSLine()) != null) {
            // Newline line indicates new sentence
            if (line.equals("\n")) {
                if (!sentence.isEmpty()) {
                    // Save completed sentence
                    sentences.add(sentence);
                    // and start a new sentence
                    sentence = new ArrayList<String>();
                }
            } else {
                // Get the tokens in the line
                List<String> tokens = getTokens(line);
                if (!tokens.isEmpty()) {
                    // If last token is an end-sentence token "</S>"
                    if (tokens.get(tokens.size() - 1).equals("</S>")) {
                        // Then remove it
                        tokens.remove(tokens.size() - 1);
                        // and add final sentence tokens
                        sentence.addAll(tokens);
                        // Save completed sentence
                        sentences.add(sentence);
                        // and start a new sentence
                        sentence = new ArrayList<String>();
                    } else {
                        // Add the tokens in the line to the current sentence
                        sentence.addAll(tokens);
                    }
                }
            }
        }
        // File should always end at end of a sentence
        assert (sentence.isEmpty());
        return sentences;
    }


    /**
     * Take a list of LDC tagged input files or directories and convert them to a List of sentences
     * each represented as a List of token Strings
     */
    public static List<List<String>> convertToTokenLists(File[] files, boolean suf, boolean mor) {
        enableSuffixGeneration = suf;
        enableMorphGeneration = mor;

        List<List<String>> sentences = new ArrayList<List<String>>();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isDirectory()) {
                if (!file.getName().contains("CHANGES.LOG"))
                    sentences.addAll(new POSTaggedFile(file).tokenLists());
            } else {
                File[] dirFiles = file.listFiles();
                sentences.addAll(convertToTokenLists(dirFiles, suf, mor));
            }
        }
        return sentences;
    }

}
