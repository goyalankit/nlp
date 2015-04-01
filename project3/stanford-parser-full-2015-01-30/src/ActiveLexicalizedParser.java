
import java.io.*;
import java.util.*;

import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.util.ScoredObject;

class ActiveLexicalizedParser {

    private static final int ITERATION_COUNT = 20;
    public static boolean BY_ITERATION_COUNT = false;
    public static int []TRAIN_WORDS_NUMBER = new int[] {2000, 5000, 10000, 50000, 75000, 100000};
    public static Treebank trainTreeBank;
    public static Treebank initialTreeBank;
    public static Map<Tree, Integer> sortedtrainSentWScore;
    public static Map<Tree, Double> sortedtrainSentWProb;
    public static List<Tree> listOfTrainData;
    public static int totalTrainWords = 0;
    public static int alreadyTrainedOn = 0;
    public static int currentTotalTrained = 2000;

    public static HashMap<Tree, Double> remainingTrainSentProb;
    public static Treebank testTreebank;

    public static PrintWriter out;
    public static long totalWords = 0;
    public static Options op;
    public static File file;
    public static int iteration = 0;
    public enum AnalysisType { RANDOM, SEN_LENGTH, SEL_PROB, TREE_ENTROPY };

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
    public static void main(String[] args) throws IOException {
        String initial_data = args[0];
        String training_data_dir = args[1];
        String testTreebankpath = args[2];
        AnalysisType type = AnalysisType.valueOf(args[3]);
        if (args.length > 4) {
            BY_ITERATION_COUNT = Boolean.parseBoolean(args[4]);
        }
        // type = AnalysisType.RANDOM;

        // options for lexicalized parser
        op = new Options();
        op.doDep = false;
        op.doPCFG = true;
        op.setOptions("-goodPCFG", "-evals", "tsv");

        System.out.println("Running...");

        // train on initial data.
        LexicalizedParser lp = LexicalizedParser.trainFromTreebank(initial_data, null, op);
        testTreebank = LexicalizedParser.getTreebankFromDir(testTreebankpath, op);
        trainTreeBank = LexicalizedParser.getTreebankFromDir(training_data_dir, op);

        // Get the treebanks
        initialTreeBank = LexicalizedParser.getTreebankFromDir(initial_data, op);

        int initialWords = 0;
        for (Tree ct : initialTreeBank) {
            initialWords += ct.yieldWords().size();
        }

        System.out.println("NLP: By iteration is: " + BY_ITERATION_COUNT);


        for (int num_words : TRAIN_WORDS_NUMBER){
            System.out.println("NLP: Training on words: " + num_words);
            iteration = 0;
            currentTotalTrained = num_words;

            lp = LexicalizedParser.trainFromTreebank(initial_data, null, op);



            // create the intermediate training file.
            initFile(num_words, type.toString());

            // write initial tree bank to file.
            appendToFile(initialTreeBank);

            switch (type) {
                case RANDOM:
                    System.out.println("NLP: Training using random selection.");
                    lp = trainByRandomSelection(lp);
                    break;

                case SEN_LENGTH:
                    System.out.println("NLP: Training using sentence length.");
                    lp = trainBySentenceLength(lp);
                    break;

                case SEL_PROB:
                    System.out.println("NLP: Training using selective probability.");
                    lp = trainByProb1(lp);
                    break;

                case TREE_ENTROPY:
                    System.out.println("NLP: Training using tree entropy.");
                    lp = trainByTreeEntropy(lp);
                    break;

            }
        }
        cleanUp();
    }


    public static void cleanUp() {

    }

    public static void printStats(double PCFG_F1) {
        System.out.println("NLP: *************************************");
        System.out.println("NLP: Total training words: " + alreadyTrainedOn);
        System.out.println("NLP: Number of iterations: " + iteration);
        System.out.println("NLP: PCFG F1: " + PCFG_F1);
        System.out.println("NLP: *************************************");
    }

    /*
    * Method 0
    *
    *
    * */
    public static LexicalizedParser trainByRandomSelection(LexicalizedParser lp) {
        listOfTrainData = new LinkedList<Tree>();
        createListOfTrainData();
        while (true) {
            System.out.println("DEBUG: Training iteration: " + iteration);
            chooseByRandomSelection();
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            System.out.println("NLP: Testing now...");
            double PCFG_F1 = test(lp, testTreebank);
            printStats(PCFG_F1);
            if (iteration == ITERATION_COUNT) break;
        }

        System.out.println("NLP: Training finished.");
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
            wordCount += tree.yieldWords().size();
            listOfTrainData.remove(num);
        }
        System.out.println("DEBUG: Length of list of trained data: " + listOfTrainData.size());
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
        while (alreadyTrainedOn < currentTotalTrained) {
            System.out.println("Training iteration: " + iteration);
            chooseByLength(1500);
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            //if (BY_ITERATION_COUNT && iteration == 4) break;
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
        HashMap<Tree, Integer> trainSentWScore = new HashMap<Tree, Integer>();
        for (Tree tree : trainTreeBank) {
            trainSentWScore.put(tree, tree.yieldWords().size());
        }
        sortedtrainSentWScore = sortByValueInteger(trainSentWScore);
    }


    private static void chooseByLength(int k) {
        if (null == sortedtrainSentWScore) {
            createHashForTreeAndLength();
        }
        List<Tree> toBeRemovedTrees = new LinkedList<Tree>();
        int i = 0;
        for (Map.Entry<Tree, Integer> entry : sortedtrainSentWScore.entrySet()) {
            if (i >= 1500) break;
            appendToFile(entry.getKey());
            toBeRemovedTrees.add(entry.getKey());
            totalWords += entry.getValue();
            alreadyTrainedOn += entry.getValue();
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
        remainingTrainSentProb = new HashMap<Tree, Double>();
        int total  = trainTreeBank.size();
        for (Tree tree : trainTreeBank) {
            System.out.println("Remaining: " + total--);
            Tree tree1 =  lp.apply(tree.yieldWords());

            // TODO check the logic here. If the normalizing factor is okay.
            remainingTrainSentProb.put(tree, tree1.score()/tree1.yieldWords().size()-1);
//            System.out.println("TESTING: second -" + tree1.score()/(tree1.yieldWords().size()-1));

        }
        sortedtrainSentWProb = sortByValueDouble(remainingTrainSentProb);
    }

    public static void resetHashForTreeAndProb(LexicalizedParser lp) {
        sortedtrainSentWProb.clear();
        HashMap<Tree, Double> trainSentWScore = new HashMap<Tree, Double>();
        for (Tree tree : remainingTrainSentProb.keySet()) {
            // Apply on untagged words.
            Tree tree1 =  lp.apply(tree.yieldWords());
            //trainSentWScore.put(tree, Math.pow(tree.score(), 1.0/(tree.yieldWords().size())));
            // TODO check the logic here. If the normalizing factor is okay.
            trainSentWScore.put(tree, tree1.score()/(tree1.yieldWords().size()-1));
        }
        sortedtrainSentWProb = sortByValueDouble(trainSentWScore);
    }


    public static LexicalizedParser trainByProb1(LexicalizedParser lp) {
        boolean first = true;
        initHashForTreeAndProb(lp);
        while ((first || sortedtrainSentWProb.size() > 0) && alreadyTrainedOn < currentTotalTrained) {
            first = false;
            System.out.println("Training iteration: " + iteration);
            chooseByProbSelectParseTree(lp);
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            //if (iteration == 20) break;
        }

        System.out.println("Training finished.");
        return lp;
    }

    public static void chooseByProbSelectParseTree(LexicalizedParser lp) {
        int wordCount = 0;
        for (Map.Entry<Tree, Double> entry : sortedtrainSentWProb.entrySet()) {
            System.out.println("Choosing by score: " + entry.getValue());
            appendToFile(entry.getKey());
            wordCount += entry.getKey().yieldWords().size();
            alreadyTrainedOn += entry.getKey().yieldWords().size();
            remainingTrainSentProb.remove(entry.getKey());
            if (wordCount >= 1500) break;
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
    private static LexicalizedParser trainByTreeEntropy(LexicalizedParser lp) {
        initHashForTreeAndEntropy(lp);
        boolean first = true;
        while (first || sortedtrainSentWProb.size() > 0) {
            first = false;
            System.out.println("Training iteration: " + iteration);
            chooseByTreeEntropy(lp);
            lp = LexicalizedParser.trainFromTreebank(file.getAbsolutePath(), null, op);
            iteration++;
            //if (iteration == 20) break;
        }
        return lp;
    }

    public static void chooseByTreeEntropy(LexicalizedParser lp) {
        int wordCount = 0;
        for (Map.Entry<Tree, Double> entry : sortedtrainSentWProb.entrySet()) {
            appendToFile(entry.getKey());
            wordCount += entry.getKey().yieldWords().size();
            remainingTrainSentProb.remove(entry.getKey());
            if (wordCount >= 1500) break;
        }
        resetHashForTreeAndEntropy(lp);
        System.out.println("Length of remaining training set: " + remainingTrainSentProb.size());
    }

    private static void resetHashForTreeAndEntropy(LexicalizedParser lp) {
        sortedtrainSentWProb.clear();
        HashMap<Tree, Double> trainSentWScore = new HashMap<Tree, Double>();
        for (Tree tree : remainingTrainSentProb.keySet()) {
            // TODO check the logic here. If the normalizing factor is okay.
            trainSentWScore.put(tree, getTreeEntropy(lp, tree)/tree.size());
        }
        sortedtrainSentWProb = sortByValueDouble(trainSentWScore);
    }

    private static void initHashForTreeAndEntropy(LexicalizedParser lp) {
        remainingTrainSentProb = new HashMap<Tree, Double>();
        int total  = trainTreeBank.size();
        for (Tree tree : trainTreeBank) {
            System.out.println("Remaining: " + total--);
            // TODO check the logic here. If the normalizing factor is okay.
            remainingTrainSentProb.put(tree, (getTreeEntropy(lp, tree)/tree.yieldWords().size()));
            //System.out.println("TESTING: second -" + tree.score() /(tree.yieldWords().size()));
        }
        sortedtrainSentWProb = sortByValueDouble(remainingTrainSentProb);

    }

     private static double getTreeEntropy(LexicalizedParser lp, Tree tree) {
        LexicalizedParserQuery lpq = lp.lexicalizedParserQuery();

        lpq.parse(tree.yieldWords());
        List<ScoredObject<Tree>> kPraseTrees = lpq.getKBestPCFGParses(10);
        double total_score = 0.0;
        for (ScoredObject<Tree> sco : kPraseTrees) {
            total_score += sco.score();
        }
         System.out.println("Total Tree Entropy: " + total_score);
        return total_score;
    }


    /* Common Methods */
    public static double test(LexicalizedParser lp, Treebank testTreebank) {
        EvaluateTreebank evaluator = new EvaluateTreebank(lp);
        return evaluator.testOnTreebank(testTreebank);
    }

    public static void initFile(int num_words, String type) {
        file = new File("training.cont."+""+type +"."+num_words);
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
