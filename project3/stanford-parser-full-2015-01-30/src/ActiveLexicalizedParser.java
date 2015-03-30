
import java.io.*;
import java.util.*;

import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;

class ActiveLexicalizedParser {

    private static final int ITERATION_COUNT = 20;
    public static Treebank trainTreeBank;
    public static Treebank initialTreeBank;
    public static Map<Tree, Integer> sortedtrainSentWScore;
    public static PrintWriter out;
    public static long totalWords = 0;
    public static Options op;
    public static File file;
    public static int iteration = 0;

    /**
     * The main method demonstrates the easiest way to load a parser.
     * Simply call loadModel and specify the path of a serialized grammar
     * model, which can be a file, a resource on the classpath, or even a URL.
     * For example, this demonstrates loading a grammar from the models jar
     * file, which you therefore need to include on the classpath for ActiveLexicalizedParser
     * to work.
     * <p/>
     * Usage: {@code java ActiveLexicalizedParser [[model] textFile]}
     * e.g.: java ActiveLexicalizedParser edu/stanford/nlp/models/lexparser/chineseFactored.ser.gz data/chinese-onesent-utf8.txt
     */
    public static void main(String[] args) {
        String initial_data = args[0];
        String training_data_dir = args[1];
        String testTreebankpath = args[2];

        // options for lexicalized parser
        op = new Options();
        op.doDep = false;
        op.doPCFG = true;
        op.setOptions("-goodPCFG", "-evals", "tsv");

        // train on initial data.
        LexicalizedParser lp = LexicalizedParser.trainFromTreebank(initial_data, null, op);
        Treebank testTreebank = LexicalizedParser.getTreebankFromDir(testTreebankpath, op);

        // Get the treebanks
        initialTreeBank = LexicalizedParser.getTreebankFromDir(initial_data, op);
        trainTreeBank = LexicalizedParser.getTreebankFromDir(training_data_dir, op);

        // create the intermediate training file.
        initFile();

        appendToFile(initialTreeBank);
        for (Tree ct : initialTreeBank) {
            totalWords += ct.yieldWords().size();
        }

        trainBySentenceLength(lp);
        System.out.println("Testing now...");
        test(lp, testTreebank);
        printStats();
    }

    public static void printStats() {
        System.out.println("*************************************");
        System.out.println("Total training words: " + totalWords);
        System.out.println("Number of iterations: " + iteration);
        System.out.println("*************************************");
    }

    /*
    * Method 0
    *
    *
    * */

    public static LexicalizedParser trainByRandomSelection() {


        return null;
    }

    /*
    *
    * Method 1: choose by sentence length.
    *
    * */
    public static LexicalizedParser trainBySentenceLength(LexicalizedParser lp) {
        createHashForTreeAndLength();
        while (sortedtrainSentWScore.size() > 0) {
            System.out.println("Training iteration: " + iteration);
            chooseByLength(1500, op);
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            if (iteration == 4) break;
        }

        System.out.println("Training finished.");
        return lp;
    }


    public static void test(LexicalizedParser lp, Treebank testTreebank) {
        EvaluateTreebank evaluator = new EvaluateTreebank(lp);
        evaluator.testOnTreebank(testTreebank);
    }

    public static void initFile() {
        file = new File("training.cont");
        try {

            FileWriter fstream = new FileWriter(file);
            out = new PrintWriter(new BufferedWriter(fstream));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void appendToFile(Treebank tree) {
        out.println(tree.toString());
        out.flush();
    }

    public static void appendToFile(Tree tree) {
        out.println(tree.toString());
        out.flush();
    }

    public static Map<Tree, Integer> sortByValue(Map<Tree, Integer> map) {
        List list = new LinkedList(map.entrySet());
        Collections.sort(list, new Comparator() {

            @Override
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
            }
        });

        Map result = new LinkedHashMap();
        for (Iterator it = list.iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // used for selection by sentence length.
    private static void createHashForTreeAndLength() {
        HashMap<Tree, Integer> trainSentWScore = new HashMap<>();
        for (Tree tree : trainTreeBank) {
            trainSentWScore.put(tree, tree.yield().size());
        }
        sortedtrainSentWScore = sortByValue(trainSentWScore);
    }


    private static void chooseByLength(int k, Options op) {
        if (null == sortedtrainSentWScore) {
            createHashForTreeAndLength();
        }
        List<Tree> toBeRemovedTrees = new LinkedList<>();
        int i = 0;
        for (Map.Entry<Tree, Integer> entry : sortedtrainSentWScore.entrySet()) {
            if (i >= k) break;
            appendToFile(entry.getKey());
            toBeRemovedTrees.add(entry.getKey());
            totalWords += entry.getValue();
            i += entry.getValue();
        }

        for (Tree tree : toBeRemovedTrees) {
            sortedtrainSentWScore.remove(tree);
        }
        System.out.println("Length of sorted hash: " + sortedtrainSentWScore.size());
    }

    private static Treebank getTopKtrees(LexicalizedParser lp) {

        LexicalizedParserQuery lpq = lp.lexicalizedParserQuery();
        for (Tree tree : trainTreeBank) {

        }
        return null;
    }

    /**
     * demoDP demonstrates turning a file into tokens and then parse
     * trees.  Note that the trees are printed by calling pennPrint on
     * the Tree object.  It is also possible to pass a PrintWriter to
     * pennPrint if you want to capture the output.
     * This code will work with any supported language.
     */
    public static void demoDP(LexicalizedParser lp, String filename) {
        // This option shows loading, sentence-segmenting and tokenizing
        // a file using DocumentPreprocessor.
        TreebankLanguagePack tlp = lp.treebankLanguagePack(); // a PennTreebankLanguagePack for English
        GrammaticalStructureFactory gsf = null;
        if (tlp.supportsGrammaticalStructures()) {
            gsf = tlp.grammaticalStructureFactory();
        }
        // You could also create a tokenizer here (as below) and pass it
        // to DocumentPreprocessor
        for (List<HasWord> sentence : new DocumentPreprocessor(filename)) {
            Tree parse = lp.apply(sentence);
            parse.pennPrint();
            System.out.println();

            if (gsf != null) {
                GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
                Collection tdl = gs.typedDependenciesCCprocessed();
                System.out.println(tdl);
                System.out.println();
            }
        }
    }

    /**
     * demoAPI demonstrates other ways of calling the parser with
     * already tokenized text, or in some cases, raw text that needs to
     * be tokenized as a single sentence.  Output is handled with a
     * TreePrint object.  Note that the options used when creating the
     * TreePrint can determine what results to print out.  Once again,
     * one can capture the output by passing a PrintWriter to
     * TreePrint.printTree. This code is for English.
     */
    public static void demoAPI(LexicalizedParser lp) {
        // This option shows parsing a list of correctly tokenized words
        String[] sent = {"This", "is", "an", "easy", "sentence", "."};
        List<CoreLabel> rawWords = Sentence.toCoreLabelList(sent);
        Tree parse = lp.apply(rawWords);
        parse.pennPrint();
        System.out.println();

        // This option shows loading and using an explicit tokenizer
        String sent2 = "This is another sentence.";
        TokenizerFactory<CoreLabel> tokenizerFactory =
                PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
        Tokenizer<CoreLabel> tok =
                tokenizerFactory.getTokenizer(new StringReader(sent2));
        List<CoreLabel> rawWords2 = tok.tokenize();
        parse = lp.apply(rawWords2);

        TreebankLanguagePack tlp = lp.treebankLanguagePack(); // PennTreebankLanguagePack for English
        GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
        GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
        List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
        System.out.println(tdl);
        System.out.println();

        // You can also use a TreePrint object to print trees and dependencies
        TreePrint tp = new TreePrint("penn,typedDependenciesCollapsed");
        tp.printTree(parse);
    }

    private ActiveLexicalizedParser() {
    } // static methods only

}
