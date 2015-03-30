
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
    public static boolean BY_ITERATION_COUNT = true;
    public static Treebank trainTreeBank;
    public static Treebank initialTreeBank;
    public static Map<Tree, Integer> sortedtrainSentWScore;
    public static Map<Tree, Double> sortedtrainSentWProb;
    public static List<Tree> listOfTrainData;

    public static HashMap<Tree, Double> remainingTrainSentProb;

    public static PrintWriter out;
    public static long totalWords = 0;
    public static Options op;
    public static File file;
    public static int iteration = 0;

    private ActiveLexicalizedParser() {
    } // static methods only

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

        //trainBySentenceLength(lp);
        //trainByRandomSelection(lp);
        trainByProb1(lp);

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

    public static LexicalizedParser trainByRandomSelection(LexicalizedParser lp) {
        listOfTrainData = new LinkedList<>();
        createListOfTrainData();
        while (listOfTrainData.size() > 0) {
            System.out.println("Training iteration: " + iteration);
            chooseByRandomSelection();
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            if (iteration == 20) break;
        }

        System.out.println("Training finished.");
        return lp;
    }

    public static void chooseByRandomSelection() {
        Random random = new Random();
        int wordCount = 0;
        while (wordCount < 1500 && listOfTrainData.size() > 0) {
            // System.out.println("Size: " + listOfTrainData.size());
            int num = random.nextInt(listOfTrainData.size());
            Tree tree = listOfTrainData.get(num);
            appendToFile(tree);
            totalWords += tree.yieldWords().size();
            wordCount += tree.yieldWords().size();
            listOfTrainData.remove(num);
        }

        System.out.println("Length of list of trained data: " + listOfTrainData.size());

    }

    public static void createListOfTrainData() {
        for (Tree tree : trainTreeBank) {
            listOfTrainData.add(tree);
        }
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
            chooseByLength(1500);
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            if (BY_ITERATION_COUNT && iteration == 4) break;
        }

        System.out.println("Training finished.");
        return lp;
    }

    // lower value is at the top.
    public static Map<Tree, Integer> sortByValueInteger(Map<Tree, Integer> map) {
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
            System.out.println("Score: "+entry.getValue());
        }
        return result;
    }

    // used for selection by sentence length.
    private static void createHashForTreeAndLength() {
        HashMap<Tree, Integer> trainSentWScore = new HashMap<>();
        for (Tree tree : trainTreeBank) {
            trainSentWScore.put(tree, tree.yield().size());
        }
        sortedtrainSentWScore = sortByValueInteger(trainSentWScore);
    }


    private static void chooseByLength(int k) {
        if (null == sortedtrainSentWScore) {
            createHashForTreeAndLength();
        }
        List<Tree> toBeRemovedTrees = new LinkedList<>();
        int i = 0;
        for (Map.Entry<Tree, Integer> entry : sortedtrainSentWScore.entrySet()) {
            if (i >= 1500) break;
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

    /*
    * Method 2
    *
    * */


    public static void initHashForTreeAndProb(LexicalizedParser lp) {
        remainingTrainSentProb = new HashMap<>();
        int total  = trainTreeBank.size();
        for (Tree tree : trainTreeBank) {
            System.out.println("Remaining: " + total--);
            tree = lp.apply(tree.taggedYield());
            System.out.println("Tree Score: " + tree.score());
            // TODO check the logic here. If the normalizing factor is okay.
            remainingTrainSentProb.put(tree, (tree.score()/tree.yieldWords().size()));

        }
        sortedtrainSentWProb = sortByValueDouble(remainingTrainSentProb);
    }

    public static void resetHashForTreeAndProb(LexicalizedParser lp) {
        sortedtrainSentWProb.clear();
        HashMap<Tree, Double> trainSentWScore = new HashMap<>();
        for (Tree tree : remainingTrainSentProb.keySet()) {
            tree = lp.apply(tree.taggedYield());
            //trainSentWScore.put(tree, Math.pow(tree.score(), 1.0/(tree.yieldWords().size())));
            // TODO check the logic here. If the normalizing factor is okay.
            trainSentWScore.put(tree, tree.score()/tree.yieldWords().size());
        }
        sortedtrainSentWProb = sortByValueDouble(trainSentWScore);
    }


    public static LexicalizedParser trainByProb1(LexicalizedParser lp) {
        boolean first = true;
        initHashForTreeAndProb(lp);
        while (first || sortedtrainSentWProb.size() > 0) {
            first = false;
            System.out.println("Training iteration: " + iteration);
            chooseByProbSelectParseTree(lp);
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            if (iteration == 20) break;
        }

        System.out.println("Training finished.");
        return lp;
    }

    public static void chooseByProbSelectParseTree(LexicalizedParser lp) {
        int wordCount = 0;
        for (Map.Entry<Tree, Double> entry : sortedtrainSentWProb.entrySet()) {
            appendToFile(entry.getKey());
            wordCount += entry.getKey().yieldWords().size();
            remainingTrainSentProb.remove(entry.getKey());
        }
        resetHashForTreeAndProb(lp);
        System.out.println("Length of remaining training set: " + remainingTrainSentProb.size());
    }

    // higher probability is at the top
    public static Map<Tree, Double> sortByValueDouble(Map<Tree, Double> map) {
        List list = new LinkedList(map.entrySet());
        Collections.sort(list, new Comparator() {

            @Override
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Map.Entry) (o2)).getValue()).compareTo(((Map.Entry) (o1)).getValue());
            }
        });

        Map result = new LinkedHashMap();
        for (Iterator it = list.iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            result.put(entry.getKey(), entry.getValue());
            System.out.println("Score: "+entry.getValue());
        }
        return result;
    }

    /*
    * Method 3
    *
    * */
    private static Treebank getTopKtrees(LexicalizedParser lp) {
        LexicalizedParserQuery lpq = lp.lexicalizedParserQuery();
        for (Tree tree : trainTreeBank) {

        }
        return null;
    }


    /* Common Methods */

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

    public static void appendToFile(Treebank treebank) {
        out.println(treebank.toString());
        out.flush();
    }

    public static void appendToFile(Tree tree) {
        out.println(tree.pennString());
        out.flush();
    }
}
